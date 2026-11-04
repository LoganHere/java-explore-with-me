package ru.practicum.statsclient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Тесты HTTP-клиента сервиса статистики")
class StatsClientTest {

    private StatsClient client;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new StatsClient("http://localhost:9090");
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        injectRestTemplate(restTemplate);
    }

    @Test
    @DisplayName("Сохранение обращения отправляет POST-запрос на /hit")
    void shouldSendHitRequest() {
        server.expect(manyTimes(), requestTo("http://localhost:9090/hit"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":1,\"app\":\"ewm-main-service\",\"uri\":\"/events/1\","
                        + "\"ip\":\"192.168.0.1\",\"timestamp\":\"2026-11-04 12:00:00\"}", MediaType.APPLICATION_JSON));

        EndpointHit saved = client.saveHit("/events/1", "192.168.0.1");

        assertEquals("/events/1", saved.getUri());
        assertEquals(LocalDateTime.of(2026, 11, 4, 12, 0), saved.getTimestamp());
        server.verify();
    }

    @Test
    @DisplayName("Если сервис статистики недоступен, сохранение обращения возвращает null")
    void shouldReturnNullWhenStatsUnavailable() {
        server.expect(manyTimes(), requestTo("http://localhost:9090/hit"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        EndpointHit saved = client.saveHit("/events/1", "192.168.0.1");

        assertNull(saved);
        server.verify();
    }

    @Test
    @DisplayName("Запрос статистики формирует корректный URL с закодированными параметрами")
    void shouldBuildStatsUrl() {
        server.expect(manyTimes(), requestTo(
                        "http://localhost:9090/stats?start=2026-11-01+00%3A00%3A00&end=2026-11-30+00%3A00%3A00"
                                + "&uris=%2Fevents%2F1&uris=%2Fevents%2F2&unique=true"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("[{\"app\":\"ewm-main-service\",\"uri\":\"/events/1\",\"hits\":5}]",
                        MediaType.APPLICATION_JSON));

        List<ViewStats> stats = client.getStats(
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 11, 30, 0, 0),
                List.of("/events/1", "/events/2"),
                true);

        assertEquals(1, stats.size());
        assertEquals(5L, stats.get(0).getHits());
        server.verify();
    }

    @Test
    @DisplayName("Если сервис статистики недоступен, запрос статистики возвращает пустой список")
    void shouldReturnEmptyListWhenStatsUnavailable() {
        server.expect(manyTimes(), requestTo("http://localhost:9090/stats"
                        + "?start=2026-11-01+00%3A00%3A00&end=2026-11-30+00%3A00%3A00&unique=false"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        List<ViewStats> stats = client.getStats(
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 11, 30, 0, 0),
                null,
                false);

        assertTrue(stats.isEmpty());
        server.verify();
    }

    private void injectRestTemplate(RestTemplate restTemplate) {
        try {
            java.lang.reflect.Field field = StatsClient.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(client, restTemplate);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось подставить RestTemplate", exception);
        }
    }
}
