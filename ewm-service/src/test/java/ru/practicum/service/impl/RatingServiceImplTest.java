package ru.practicum.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.practicum.dto.EventRatingDto;
import ru.practicum.dto.NewRatingDto;
import ru.practicum.dto.RatingDto;
import ru.practicum.dto.UserRatingDto;
import ru.practicum.entity.Category;
import ru.practicum.entity.Event;
import ru.practicum.entity.EventState;
import ru.practicum.entity.Rating;
import ru.practicum.entity.RatingReaction;
import ru.practicum.entity.User;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.RatingRepository;
import ru.practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Юнит-тесты сервиса рейтингов")
class RatingServiceImplTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RatingServiceImpl ratingService;

    @Test
    @DisplayName("Лайк увеличивает счётчик лайков события")
    void shouldAddLike() {
        Event event = buildEvent(EventState.PUBLISHED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildUser(2L, "Пётр Иванов")));
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));
        when(ratingRepository.existsByEventIdAndUserId(1L, 2L)).thenReturn(false);
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> {
            Rating rating = invocation.getArgument(0);
            rating.setId(5L);
            return rating;
        });

        RatingDto dto = ratingService.addRating(2L, 1L, new NewRatingDto(RatingReaction.LIKE));

        assertEquals(5L, dto.getId());
        assertEquals(1L, dto.getEvent());
        assertEquals(2L, dto.getUser());
        assertEquals(RatingReaction.LIKE, dto.getReaction());
        assertEquals(1L, event.getLikes());
        assertEquals(0L, event.getDislikes());
        verify(eventRepository).save(event);
    }

    @Test
    @DisplayName("Дизлайк увеличивает счётчик дизлайков события")
    void shouldAddDislike() {
        Event event = buildEvent(EventState.PUBLISHED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildUser(2L, "Пётр Иванов")));
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ratingService.addRating(2L, 1L, new NewRatingDto(RatingReaction.DISLIKE));

        assertEquals(0L, event.getLikes());
        assertEquals(1L, event.getDislikes());
    }

    @Test
    @DisplayName("Инициатор события не может оценить собственное событие")
    void shouldRejectRatingOfOwnEvent() {
        Event event = buildEvent(EventState.PUBLISHED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser(1L, "Иван Петров")));
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> ratingService.addRating(1L, 1L, new NewRatingDto(RatingReaction.LIKE)));

        assertEquals("Инициатор события не может оценивать собственное событие", exception.getMessage());
        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Нельзя оценить неопубликованное событие")
    void shouldRejectRatingOfUnpublishedEvent() {
        Event event = buildEvent(EventState.PENDING);
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildUser(2L, "Пётр Иванов")));
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> ratingService.addRating(2L, 1L, new NewRatingDto(RatingReaction.LIKE)));

        assertEquals("Оценивать можно только опубликованные события", exception.getMessage());
    }

    @Test
    @DisplayName("Пользователь не может оценить одно событие дважды")
    void shouldRejectRepeatedRating() {
        Event event = buildEvent(EventState.PUBLISHED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildUser(2L, "Пётр Иванов")));
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));
        when(ratingRepository.existsByEventIdAndUserId(1L, 2L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> ratingService.addRating(2L, 1L, new NewRatingDto(RatingReaction.LIKE)));
        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Оценка несуществующего события завершается ошибкой")
    void shouldRejectRatingOfUnknownEvent() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildUser(2L, "Пётр Иванов")));
        when(eventRepository.findByIdWithCategoryAndInitiator(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> ratingService.addRating(2L, 99L, new NewRatingDto(RatingReaction.LIKE)));
    }

    @Test
    @DisplayName("Изменение реакции переносит счётчик из лайков в дизлайки")
    void shouldChangeReaction() {
        Event event = buildEvent(EventState.PUBLISHED);
        event.setLikes(3L);
        Rating rating = Rating.builder()
                .id(7L)
                .event(event)
                .user(buildUser(2L, "Пётр Иванов"))
                .reaction(RatingReaction.LIKE)
                .created(LocalDateTime.now())
                .build();
        when(ratingRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(rating));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RatingDto dto = ratingService.updateRating(2L, 1L, new NewRatingDto(RatingReaction.DISLIKE));

        assertEquals(RatingReaction.DISLIKE, dto.getReaction());
        assertEquals(2L, event.getLikes());
        assertEquals(1L, event.getDislikes());
    }

    @Test
    @DisplayName("Повторная установка той же реакции не меняет счётчики")
    void shouldKeepCountersOnSameReaction() {
        Event event = buildEvent(EventState.PUBLISHED);
        event.setDislikes(2L);
        Rating rating = Rating.builder()
                .id(7L)
                .event(event)
                .user(buildUser(2L, "Пётр Иванов"))
                .reaction(RatingReaction.DISLIKE)
                .created(LocalDateTime.now())
                .build();
        when(ratingRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(rating));

        RatingDto dto = ratingService.updateRating(2L, 1L, new NewRatingDto(RatingReaction.DISLIKE));

        assertEquals(RatingReaction.DISLIKE, dto.getReaction());
        assertEquals(0L, event.getLikes());
        assertEquals(2L, event.getDislikes());
        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Изменение отсутствующей реакции завершается ошибкой")
    void shouldRejectUpdateOfMissingRating() {
        when(ratingRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> ratingService.updateRating(2L, 1L, new NewRatingDto(RatingReaction.LIKE)));
    }

    @Test
    @DisplayName("Удаление реакции уменьшает счётчик лайков")
    void shouldDeleteRating() {
        Event event = buildEvent(EventState.PUBLISHED);
        event.setLikes(1L);
        Rating rating = Rating.builder()
                .id(7L)
                .event(event)
                .user(buildUser(2L, "Пётр Иванов"))
                .reaction(RatingReaction.LIKE)
                .created(LocalDateTime.now())
                .build();
        when(ratingRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(rating));

        ratingService.deleteRating(2L, 1L);

        assertEquals(0L, event.getLikes());
        verify(eventRepository).save(event);
        verify(ratingRepository).delete(rating);
    }

    @Test
    @DisplayName("Удаление отсутствующей реакции завершается ошибкой")
    void shouldRejectDeleteOfMissingRating() {
        when(ratingRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> ratingService.deleteRating(2L, 1L));
    }

    @Test
    @DisplayName("Рейтинг события рассчитывается как разница лайков и дизлайков")
    void shouldReturnEventRating() {
        Event event = buildEvent(EventState.PUBLISHED);
        event.setLikes(5L);
        event.setDislikes(2L);
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));

        EventRatingDto dto = ratingService.getEventRating(1L);

        assertEquals(1L, dto.getEventId());
        assertEquals(5L, dto.getLikes());
        assertEquals(2L, dto.getDislikes());
        assertEquals(3L, dto.getRating());
    }

    @Test
    @DisplayName("Рейтинг неопубликованного события недоступен публично")
    void shouldHideRatingOfUnpublishedEvent() {
        when(eventRepository.findByIdWithCategoryAndInitiator(1L))
                .thenReturn(Optional.of(buildEvent(EventState.PENDING)));

        assertThrows(NotFoundException.class, () -> ratingService.getEventRating(1L));
    }

    @Test
    @DisplayName("Рейтинг автора складывается из рейтингов его событий")
    void shouldReturnAuthorRating() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser(1L, "Иван Петров")));
        when(eventRepository.sumLikesByInitiatorId(1L)).thenReturn(7L);
        when(eventRepository.sumDislikesByInitiatorId(1L)).thenReturn(2L);
        when(eventRepository.countByInitiatorId(1L)).thenReturn(3L);

        UserRatingDto dto = ratingService.getAuthorRating(1L);

        assertEquals(1L, dto.getUserId());
        assertEquals("Иван Петров", dto.getName());
        assertEquals(7L, dto.getLikes());
        assertEquals(2L, dto.getDislikes());
        assertEquals(5L, dto.getRating());
        assertEquals(3L, dto.getEvents());
    }

    @Test
    @DisplayName("Рейтинг несуществующего автора недоступен")
    void shouldRejectAuthorRatingOfUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> ratingService.getAuthorRating(99L));
    }

    private User buildUser(Long id, String name) {
        return User.builder().id(id).name(name).email("user" + id + "@mail.ru").build();
    }

    private Event buildEvent(EventState state) {
        return Event.builder()
                .id(1L)
                .annotation("Аннотация события длиной больше двадцати символов")
                .category(Category.builder().id(1L).name("Концерты").build())
                .createdOn(LocalDateTime.now().minusDays(1))
                .description("Описание события длиной больше двадцати символов")
                .eventDate(LocalDateTime.now().plusMonths(1))
                .initiator(buildUser(1L, "Иван Петров"))
                .lat(55.75)
                .lon(37.62)
                .likes(0L)
                .dislikes(0L)
                .paid(true)
                .participantLimit(10L)
                .requestModeration(true)
                .state(state)
                .title("Событие")
                .build();
    }
}
