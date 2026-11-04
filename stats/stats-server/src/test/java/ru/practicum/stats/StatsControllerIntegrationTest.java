package ru.practicum.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.statsdto.EndpointHit;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Интеграционные тесты API сервиса статистики")
class StatsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ru.practicum.stats.repository.StatsRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Сохранение информации о запросе возвращает статус 201 и сохранённые данные")
    void shouldSaveHit() throws Exception {
        String body = "{\"app\":\"ewm-main-service\",\"uri\":\"/events/1\",\"ip\":\"192.168.0.1\","
                + "\"timestamp\":\"2026-11-04 12:00:00\"}";

        mockMvc.perform(post("/hit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.app", is("ewm-main-service")))
                .andExpect(jsonPath("$.uri", is("/events/1")))
                .andExpect(jsonPath("$.ip", is("192.168.0.1")))
                .andExpect(jsonPath("$.timestamp", is("2026-11-04 12:00:00")));

        assertEquals(1L, repository.count());
    }

    @Test
    @DisplayName("Получение статистики без параметра uris возвращает записи по всем URI")
    void shouldReturnStatsWithoutUris() throws Exception {
        saveHit("/events", "192.168.0.1", LocalDateTime.of(2026, 11, 1, 10, 0));
        saveHit("/events", "192.168.0.2", LocalDateTime.of(2026, 11, 1, 11, 0));
        saveHit("/events/1", "192.168.0.1", LocalDateTime.of(2026, 11, 1, 12, 0));

        mockMvc.perform(get("/stats")
                        .param("start", "2026-10-01 00:00:00")
                        .param("end", "2026-12-01 00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].uri", is("/events")))
                .andExpect(jsonPath("$[0].hits", is(2)))
                .andExpect(jsonPath("$[1].uri", is("/events/1")))
                .andExpect(jsonPath("$[1].hits", is(1)));
    }

    @Test
    @DisplayName("Получение статистики с unique=true считает только уникальные IP")
    void shouldReturnUniqueStats() throws Exception {
        saveHit("/events", "192.168.0.1", LocalDateTime.of(2026, 11, 1, 10, 0));
        saveHit("/events", "192.168.0.1", LocalDateTime.of(2026, 11, 1, 11, 0));
        saveHit("/events", "192.168.0.2", LocalDateTime.of(2026, 11, 1, 12, 0));

        mockMvc.perform(get("/stats")
                        .param("start", "2026-10-01 00:00:00")
                        .param("end", "2026-12-01 00:00:00")
                        .param("uris", "/events")
                        .param("unique", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hits", is(2)));

        mockMvc.perform(get("/stats")
                        .param("start", "2026-10-01 00:00:00")
                        .param("end", "2026-12-01 00:00:00")
                        .param("uris", "/events")
                        .param("unique", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hits", is(3)));
    }

    @Test
    @DisplayName("Получение статистики по нескольким URI возвращает записи для каждого URI")
    void shouldReturnStatsBySeveralUris() throws Exception {
        saveHit("/events/1", "192.168.0.1", LocalDateTime.of(2026, 11, 1, 10, 0));
        saveHit("/events/2", "192.168.0.1", LocalDateTime.of(2026, 11, 1, 11, 0));
        saveHit("/events/2", "192.168.0.2", LocalDateTime.of(2026, 11, 1, 12, 0));

        mockMvc.perform(get("/stats")
                        .param("start", "2026-10-01 00:00:00")
                        .param("end", "2026-12-01 00:00:00")
                        .param("uris", "/events/1", "/events/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].uri", is("/events/2")))
                .andExpect(jsonPath("$[0].hits", is(2)))
                .andExpect(jsonPath("$[1].uri", is("/events/1")))
                .andExpect(jsonPath("$[1].hits", is(1)));
    }

    @Test
    @DisplayName("Запрос статистики с началом периода позже конца возвращает статус 400")
    void shouldReturnBadRequestWhenStartAfterEnd() throws Exception {
        mockMvc.perform(get("/stats")
                        .param("start", "2035-05-05 00:00:00")
                        .param("end", "2020-05-05 00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Запрос статистики без обязательных параметров возвращает статус 400")
    void shouldReturnBadRequestWhenParamsMissing() throws Exception {
        mockMvc.perform(get("/stats")
                        .param("end", "2020-05-05 00:00:00"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/stats")
                        .param("start", "2020-05-05 00:00:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Запрос статистики с некорректным форматом даты возвращает статус 400")
    void shouldReturnBadRequestWhenDateInvalid() throws Exception {
        mockMvc.perform(get("/stats")
                        .param("start", "не дата")
                        .param("end", "2020-06-05 00:00:00"))
                .andExpect(status().isBadRequest());
    }

    private void saveHit(String uri, String ip, LocalDateTime timestamp) {
        EndpointHit hit = EndpointHit.builder()
                .app("ewm-main-service")
                .uri(uri)
                .ip(ip)
                .timestamp(timestamp)
                .build();
        repository.save(ru.practicum.stats.mapper.StatsMapper.toEntity(hit));
    }
}
