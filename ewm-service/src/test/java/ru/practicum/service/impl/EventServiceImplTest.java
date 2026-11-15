package ru.practicum.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.practicum.dto.EventFullDto;
import ru.practicum.dto.EventShortDto;
import ru.practicum.dto.NewEventDto;
import ru.practicum.dto.UpdateEventAdminRequest;
import ru.practicum.dto.UpdateEventUserRequest;
import ru.practicum.entity.Category;
import ru.practicum.entity.Event;
import ru.practicum.entity.EventState;
import ru.practicum.entity.User;
import ru.practicum.exception.BadRequestException;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;
import ru.practicum.repository.UserRepository;
import ru.practicum.service.StatsService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Юнит-тесты сервиса событий")
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ParticipationRequestRepository requestRepository;

    @Mock
    private StatsService statsService;

    @InjectMocks
    private EventServiceImpl eventService;

    @Test
    @DisplayName("Событие нельзя создать на дату, которая наступит раньше чем через два часа")
    void shouldRejectEventDateTooSoon() {
        NewEventDto dto = NewEventDto.builder()
                .annotation("Аннотация события длиной больше двадцати символов")
                .category(1L)
                .description("Описание события длиной больше двадцати символов")
                .eventDate(LocalDateTime.now().plusHours(1))
                .location(new ru.practicum.dto.Location(55.75, 37.62))
                .title("Событие")
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser()));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(buildCategory()));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> eventService.addUserEvent(1L, dto));

        assertEquals("Дата события должна быть не раньше чем через 2 час(а) от текущего момента",
                exception.getMessage());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Событие без категории не создаётся")
    void shouldRejectEventWithUnknownCategory() {
        NewEventDto dto = NewEventDto.builder()
                .annotation("Аннотация события длиной больше двадцати символов")
                .category(99L)
                .description("Описание события длиной больше двадцати символов")
                .eventDate(LocalDateTime.now().plusDays(1))
                .location(new ru.practicum.dto.Location(55.75, 37.62))
                .title("Событие")
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser()));

        assertThrows(NotFoundException.class, () -> eventService.addUserEvent(1L, dto));
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Пользователь не может изменить опубликованное событие")
    void shouldRejectUpdateOfPublishedEvent() {
        Event event = buildEvent(EventState.PUBLISHED);
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> eventService.updateUserEvent(1L, 1L, new UpdateEventUserRequest()));

        assertEquals("Изменять можно только отменённые события или события в ожидании публикации",
                exception.getMessage());
    }

    @Test
    @DisplayName("Администратор не может опубликовать событие повторно")
    void shouldRejectPublishOfPublishedEvent() {
        Event event = buildEvent(EventState.PUBLISHED);
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> eventService.updateEventByAdmin(1L, UpdateEventAdminRequest.builder()
                        .stateAction("PUBLISH_EVENT")
                        .build()));

        assertEquals("Публиковать можно только события в ожидании публикации", exception.getMessage());
    }

    @Test
    @DisplayName("Администратор не может отклонить опубликованное событие")
    void shouldRejectRejectOfPublishedEvent() {
        Event event = buildEvent(EventState.PUBLISHED);
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));

        assertThrows(ConflictException.class, () -> eventService.updateEventByAdmin(1L,
                UpdateEventAdminRequest.builder().stateAction("REJECT_EVENT").build()));
    }

    @Test
    @DisplayName("Нельзя запросить событие, которого нет у пользователя")
    void shouldRejectForeignEvent() {
        Event event = buildEvent(EventState.PENDING);
        event.getInitiator().setId(2L);
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));

        assertThrows(NotFoundException.class, () -> eventService.getUserEvent(1L, 1L));
    }

    @Test
    @DisplayName("Поиск событий с некорректным диапазоном дат завершается ошибкой")
    void shouldRejectInvalidRange() {
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> eventService.getPublishedEvents(null, null, null,
                        LocalDateTime.of(2027, 1, 1, 0, 0),
                        LocalDateTime.of(2020, 1, 1, 0, 0),
                        false, "EVENT_DATE", 0, 10, "127.0.0.1"));

        assertEquals("Дата начала диапазона не может быть позже даты конца диапазона",
                exception.getMessage());
        verify(eventRepository, never()).searchPublishedEvents(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(Pageable.class));
    }

    @Test
    @DisplayName("События пользователя преобразуются в краткие DTO с просмотрами")
    void shouldReturnUserEventsWithViews() {
        Event event = buildEvent(EventState.PENDING);
        when(eventRepository.findByInitiatorId(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        when(statsService.getViewsForEvents(any())).thenReturn(java.util.Map.of(1L, 42L));
        when(requestRepository.findByEventIdInAndStatus(any(), any())).thenReturn(List.of());

        List<EventShortDto> events = eventService.getUserEvents(1L, 0, 10);

        assertEquals(1, events.size());
        assertEquals(42L, events.get(0).getViews());
        assertEquals(0L, events.get(0).getConfirmedRequests());
        verify(eventRepository).findByInitiatorId(1L,
                PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "eventDate")));
    }

    @Test
    @DisplayName("Публичное событие недоступно, если оно не опубликовано")
    void shouldHideUnpublishedEvent() {
        Event event = buildEvent(EventState.PENDING);
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));

        assertThrows(NotFoundException.class, () -> eventService.getPublishedEvent(1L, "127.0.0.1"));
        verify(statsService, never()).saveHit(any(), any());
    }

    @Test
    @DisplayName("При публикации события фиксируется обращение к нему")
    void shouldSaveHitWhenEventRequested() {
        Event event = buildEvent(EventState.PUBLISHED);
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));
        when(requestRepository.countByEventIdAndStatus(1L,
                ru.practicum.entity.RequestStatus.CONFIRMED)).thenReturn(3L);
        when(statsService.getViewsForEvent(1L)).thenReturn(5L);

        EventFullDto dto = eventService.getPublishedEvent(1L, "127.0.0.1");

        assertEquals(5L, dto.getViews());
        assertEquals(3L, dto.getConfirmedRequests());
        verify(statsService).saveHit("/events/1", "127.0.0.1");
    }

    private User buildUser() {
        return User.builder().id(1L).name("Иван Петров").email("ivan@mail.ru").build();
    }

    private Category buildCategory() {
        return Category.builder().id(1L).name("Концерты").build();
    }

    private Event buildEvent(EventState state) {
        return Event.builder()
                .id(1L)
                .annotation("Аннотация события длиной больше двадцати символов")
                .category(buildCategory())
                .createdOn(LocalDateTime.now().minusDays(1))
                .description("Описание события длиной больше двадцати символов")
                .eventDate(LocalDateTime.now().plusMonths(1))
                .initiator(buildUser())
                .lat(55.75)
                .lon(37.62)
                .paid(true)
                .participantLimit(10L)
                .requestModeration(true)
                .state(state)
                .title("Событие")
                .build();
    }
}
