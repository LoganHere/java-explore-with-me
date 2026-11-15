package ru.practicum;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.statsclient.StatsClient;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Интеграционные тесты рейтинга событий")
class RatingIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StatsClient statsClient;

    private Long authorId;

    private Long viewerId;

    private Long eventId;

    @BeforeEach
    void setUp() throws Exception {
        when(statsClient.getStats(any(LocalDateTime.class), any(LocalDateTime.class), anyList(), anyBoolean()))
                .thenReturn(List.of(new ViewStats("ewm-main-service", "/events/1", 7L)));
        when(statsClient.saveHit(any(), any(), any())).thenReturn(null);

        int sequence = SEQUENCE.incrementAndGet();
        authorId = createUser("Efremov Nikita", "loganhepe" + sequence + "@yandex.ru");
        viewerId = createUser("Nikita Viewer", "loganhepe.viewer" + sequence + "@yandex.ru");
        long categoryId = createCategory("Рейтинги " + sequence);
        eventId = createPublishedEvent(authorId, categoryId, "2029-06-01 15:10:05", "Концерт " + sequence);
    }

    @Test
    @DisplayName("Пользователь ставит лайк событию и видит рейтинг")
    void shouldAddLike() throws Exception {
        mockMvc.perform(post("/users/{userId}/events/{eventId}/rating", viewerId, eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.event", is(eventId.intValue())))
                .andExpect(jsonPath("$.user", is(viewerId.intValue())))
                .andExpect(jsonPath("$.reaction", is("LIKE")))
                .andExpect(jsonPath("$.created").exists());

        mockMvc.perform(get("/users/{userId}/events/{eventId}/rating", viewerId, eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reaction", is("LIKE")));

        mockMvc.perform(get("/ratings/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is(eventId.intValue())))
                .andExpect(jsonPath("$.likes", is(1)))
                .andExpect(jsonPath("$.dislikes", is(0)))
                .andExpect(jsonPath("$.rating", is(1)));

        mockMvc.perform(get("/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes", is(1)))
                .andExpect(jsonPath("$.dislikes", is(0)))
                .andExpect(jsonPath("$.rating", is(1)));
    }

    @Test
    @DisplayName("Повторная оценка того же события отклоняется")
    void shouldRejectRepeatedRating() throws Exception {
        addRating(viewerId, eventId, "LIKE");

        mockMvc.perform(post("/users/{userId}/events/{eventId}/rating", viewerId, eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"DISLIKE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Инициатор не может оценить собственное событие")
    void shouldRejectRatingOfOwnEvent() throws Exception {
        mockMvc.perform(post("/users/{userId}/events/{eventId}/rating", authorId, eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Изменение реакции меняет счётчики события")
    void shouldUpdateReaction() throws Exception {
        addRating(viewerId, eventId, "LIKE");

        mockMvc.perform(patch("/users/{userId}/events/{eventId}/rating", viewerId, eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"DISLIKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reaction", is("DISLIKE")));

        mockMvc.perform(get("/ratings/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes", is(0)))
                .andExpect(jsonPath("$.dislikes", is(1)))
                .andExpect(jsonPath("$.rating", is(-1)));
    }

    @Test
    @DisplayName("Изменение несуществующей реакции завершается ошибкой 404")
    void shouldRejectUpdateOfMissingRating() throws Exception {
        mockMvc.perform(patch("/users/{userId}/events/{eventId}/rating", viewerId, eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Пользователь удаляет свою реакцию")
    void shouldDeleteRating() throws Exception {
        addRating(viewerId, eventId, "LIKE");

        mockMvc.perform(delete("/users/{userId}/events/{eventId}/rating", viewerId, eventId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/{userId}/events/{eventId}/rating", viewerId, eventId))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/users/{userId}/events/{eventId}/rating", viewerId, eventId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/ratings/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes", is(0)))
                .andExpect(jsonPath("$.rating", is(0)));
    }

    @Test
    @DisplayName("Нельзя оценить неопубликованное событие")
    void shouldRejectRatingOfUnpublishedEvent() throws Exception {
        long categoryId = createCategory("Рейтинги черновиков " + SEQUENCE.incrementAndGet());
        long draftEventId = createEvent(authorId, categoryId, "2029-07-01 15:10:05", "Черновик");

        mockMvc.perform(post("/users/{userId}/events/{eventId}/rating", viewerId, draftEventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/ratings/events/{eventId}", draftEventId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Оценка несуществующего события или пользователя завершается ошибкой 404")
    void shouldReturnNotFoundForUnknownObjects() throws Exception {
        mockMvc.perform(post("/users/{userId}/events/{eventId}/rating", viewerId, 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/users/{userId}/events/{eventId}/rating", 999999L, eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/ratings/events/{eventId}", 999999L))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/ratings/users/{userId}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Некорректная реакция отклоняется с кодом 400")
    void shouldRejectInvalidReaction() throws Exception {
        mockMvc.perform(post("/users/{userId}/events/{eventId}/rating", viewerId, eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"SUPER\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/users/{userId}/events/{eventId}/rating", viewerId, eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Рейтинг автора складывается из реакций на его события")
    void shouldReturnAuthorRating() throws Exception {
        addRating(viewerId, eventId, "LIKE");

        mockMvc.perform(get("/ratings/users/{userId}", authorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(authorId.intValue())))
                .andExpect(jsonPath("$.name", is("Efremov Nikita")))
                .andExpect(jsonPath("$.likes", is(1)))
                .andExpect(jsonPath("$.dislikes", is(0)))
                .andExpect(jsonPath("$.rating", is(1)));
    }

    @Test
    @DisplayName("События можно сортировать по рейтингу")
    void shouldSortEventsByRating() throws Exception {
        long categoryId = createCategory("Рейтинги сортировки " + SEQUENCE.incrementAndGet());
        long secondEventId = createPublishedEvent(authorId, categoryId, "2029-08-01 15:10:05", "Второй концерт");
        addRating(viewerId, secondEventId, "LIKE");
        addRating(viewerId, eventId, "LIKE");

        mockMvc.perform(get("/events").param("sort", "RATING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").exists());

        mockMvc.perform(get("/events").param("sort", "RATING").param("from", "0").param("size", "5"))
                .andExpect(status().isOk());
    }

    private void addRating(long userId, long event, String reaction) throws Exception {
        mockMvc.perform(post("/users/{userId}/events/{eventId}/rating", userId, event)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"" + reaction + "\"}"))
                .andExpect(status().isCreated());
    }

    private long createUser(String name, String email) throws Exception {
        String response = mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readId(response);
    }

    private long createCategory(String name) throws Exception {
        String response = mockMvc.perform(post("/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readId(response);
    }

    private long createEvent(long userId, long categoryId, String eventDate, String title) throws Exception {
        String response = mockMvc.perform(post("/users/{userId}/events", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody(categoryId, eventDate, title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readId(response);
    }

    private long createPublishedEvent(long userId, long categoryId, String eventDate, String title)
            throws Exception {
        long createdEventId = createEvent(userId, categoryId, eventDate, title);
        mockMvc.perform(patch("/admin/events/{eventId}", createdEventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateAction\":\"PUBLISH_EVENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("PUBLISHED")));
        return createdEventId;
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

    private long readId(String response) throws Exception {
        JsonNode node = objectMapper.readTree(response);
        return node.get("id").asLong();
    }
}
