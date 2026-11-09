package ru.practicum;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.statsclient.StatsClient;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Интеграционные тесты основного сервиса")
class EwmServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StatsClient statsClient;

    @BeforeEach
    void setUp() {
        when(statsClient.getStats(any(LocalDateTime.class), any(LocalDateTime.class), anyList(), anyBoolean()))
                .thenReturn(List.of(new ViewStats("ewm-main-service", "/events/1", 7L)));
        when(statsClient.saveHit(any(), any(), any())).thenReturn(null);
    }

    @Test
    @DisplayName("Полный сценарий: пользователь, категория, событие, публикация, заявка")
    void shouldProcessFullScenario() throws Exception {
        long userId = createUser("{\"name\":\"Иван Петров\",\"email\":\"ivan.petrov@mail.ru\"}");
        long categoryId = createCategory("{\"name\":\"Концерты\"}");

        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Иван Петров\",\"email\":\"ivan.petrov@mail.ru\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Концерты\"}"))
                .andExpect(status().isConflict());

        long eventId = createEvent(userId, categoryId, "2027-06-01 15:10:05", "Сплав на байдарках");

        mockMvc.perform(get("/events/{id}", eventId))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/admin/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateAction\":\"PUBLISH_EVENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("PUBLISHED")))
                .andExpect(jsonPath("$.publishedOn", notNullValue()));

        mockMvc.perform(patch("/admin/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateAction\":\"PUBLISH_EVENT\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.views", is(7)))
                .andExpect(jsonPath("$.eventDate", is("2027-06-01 15:10:05")))
                .andExpect(jsonPath("$.confirmedRequests", is(0)));

        long requesterId = createUser("{\"name\":\"Пётр Иванов\",\"email\":\"petr.ivanov@mail.ru\"}");

        mockMvc.perform(post("/users/{userId}/requests", requesterId)
                        .param("eventId", String.valueOf(eventId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.event", is((int) eventId)));

        mockMvc.perform(post("/users/{userId}/requests", requesterId)
                        .param("eventId", String.valueOf(eventId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/users/{userId}/requests", userId)
                        .param("eventId", String.valueOf(eventId)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/users/{userId}/events/{eventId}/requests", userId, eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status", is("PENDING")));

        mockMvc.perform(patch("/users/{userId}/events/{eventId}/requests", userId, eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestIds\":[1],\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedRequests[0].status", is("CONFIRMED")))
                .andExpect(jsonPath("$.rejectedRequests", is(List.of())));

        mockMvc.perform(get("/events?from=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].views", is(7)))
                .andExpect(jsonPath("$[0].confirmedRequests", is(1)));
    }

    @Test
    @DisplayName("Валидация данных нового события")
    void shouldValidateNewEvent() throws Exception {
        long userId = createUser("{\"name\":\"Иван Петров\",\"email\":\"ivan@mail.ru\"}");
        long categoryId = createCategory("{\"name\":\"Выставки\"}");

        mockMvc.perform(post("/users/{userId}/events", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody(categoryId, "2027-06-01 15:10:05", "Сплав на байдарках")
                                .replace("\"title\":\"Сплав на байдарках\"", "\"title\":\"я\"")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/users/{userId}/events", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody(categoryId, "2020-06-01 15:10:05", "Сплав на байдарках")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/users/{userId}/events", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody(categoryId, "2027-06-01 15:10:05", "Сплав на байдарках")
                                .replace("\"participantLimit\":10", "\"participantLimit\":-6")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/users/{userId}/events", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody(categoryId, "2027-06-01 15:10:05", "Сплав на байдарках")
                                .replace("\"description\":\"Сплав на байдарках похож на полет. "
                                        + "На спокойной воде — это парение.\",", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Валидация данных нового пользователя")
    void shouldValidateNewUser() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Иван\",\"email\":\"ivan.petrov\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Иван\",\"email\":\"a@a.r\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"И\",\"email\":\"ivan.petrov@mail.ru\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Поиск событий с некорректным диапазоном дат")
    void shouldRejectInvalidRange() throws Exception {
        mockMvc.perform(get("/events")
                        .param("rangeStart", "2027-06-01 15:10:05")
                        .param("rangeEnd", "2020-06-01 15:10:05"))
                .andExpect(status().isBadRequest());
    }

    private long createUser(String body) throws Exception {
        String response = mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createCategory(String body) throws Exception {
        String response = mockMvc.perform(post("/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createEvent(long userId, long categoryId, String eventDate, String title) throws Exception {
        String response = mockMvc.perform(post("/users/{userId}/events", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody(categoryId, eventDate, title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String eventBody(long categoryId, String eventDate, String title) {
        return "{\"annotation\":\"Сплав на байдарках похож на полет и приключение\","
                + "\"category\":" + categoryId + ","
                + "\"description\":\"Сплав на байдарках похож на полет. На спокойной воде — это парение.\","
                + "\"eventDate\":\"" + eventDate + "\","
                + "\"location\":{\"lat\":55.754167,\"lon\":37.62},"
                + "\"paid\":true,\"participantLimit\":10,\"requestModeration\":true,"
                + "\"title\":\"" + title + "\"}";
    }
}
