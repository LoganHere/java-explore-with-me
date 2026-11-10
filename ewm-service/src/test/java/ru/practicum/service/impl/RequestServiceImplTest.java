package ru.practicum.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.practicum.dto.EventRequestStatusUpdateRequest;
import ru.practicum.dto.ParticipationRequestDto;
import ru.practicum.entity.Event;
import ru.practicum.entity.EventState;
import ru.practicum.entity.ParticipationRequest;
import ru.practicum.entity.RequestStatus;
import ru.practicum.entity.User;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;
import ru.practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Юнит-тесты сервиса заявок на участие")
class RequestServiceImplTest {

    @Mock
    private ParticipationRequestRepository requestRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RequestServiceImpl requestService;

    @Test
    @DisplayName("Инициатор события не может отправить заявку на участие в нём")
    void shouldRejectRequestFromInitiator() {
        Event event = buildEvent(EventState.PUBLISHED, true, 10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser(1L)));
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));

        assertThrows(ConflictException.class, () -> requestService.addRequest(1L, 1L));
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Нельзя отправить заявку на участие в неопубликованном событии")
    void shouldRejectRequestToUnpublishedEvent() {
        Event event = buildEvent(EventState.PENDING, true, 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildUser(2L)));
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));

        assertThrows(ConflictException.class, () -> requestService.addRequest(2L, 1L));
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Нельзя отправить повторную заявку на участие в событии")
    void shouldRejectDuplicateRequest() {
        Event event = buildEvent(EventState.PUBLISHED, true, 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildUser(2L)));
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));
        when(requestRepository.findByEventIdAndRequesterId(1L, 2L)).thenReturn(buildRequest(1L, 2L,
                RequestStatus.PENDING));

        assertThrows(ConflictException.class, () -> requestService.addRequest(2L, 1L));
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Нельзя отправить заявку, если достигнут лимит участников")
    void shouldRejectRequestWhenLimitReached() {
        Event event = buildEvent(EventState.PUBLISHED, true, 1L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildUser(2L)));
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));
        when(requestRepository.findByEventIdAndRequesterId(1L, 2L)).thenReturn(null);
        when(requestRepository.countByEventIdAndStatus(1L, RequestStatus.CONFIRMED)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> requestService.addRequest(2L, 1L));
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Заявка на событие без пре-модерации подтверждается сразу")
    void shouldConfirmRequestWithoutModeration() {
        Event event = buildEvent(EventState.PUBLISHED, false, 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildUser(2L)));
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));
        when(requestRepository.findByEventIdAndRequesterId(1L, 2L)).thenReturn(null);
        when(requestRepository.countByEventIdAndStatus(1L, RequestStatus.CONFIRMED)).thenReturn(0L);
        when(requestRepository.save(any(ParticipationRequest.class))).thenAnswer(invocation -> {
            ParticipationRequest saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        ParticipationRequestDto dto = requestService.addRequest(2L, 1L);

        assertEquals(RequestStatus.CONFIRMED, dto.getStatus());
    }

    @Test
    @DisplayName("Нельзя подтвердить заявку повторно")
    void shouldRejectConfirmOfConfirmedRequest() {
        Event event = buildEvent(EventState.PUBLISHED, true, 10L);
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));
        when(requestRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(buildRequest(1L, 2L, RequestStatus.CONFIRMED)));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> requestService.updateRequestStatuses(1L, 1L,
                        EventRequestStatusUpdateRequest.builder()
                                .requestIds(List.of(1L))
                                .status("CONFIRMED")
                                .build()));

        assertEquals("Изменять статус можно только у заявок в ожидании подтверждения",
                exception.getMessage());
    }

    @Test
    @DisplayName("Заявку другого события нельзя изменить через текущее событие")
    void shouldRejectRequestOfAnotherEvent() {
        Event event = buildEvent(EventState.PUBLISHED, true, 10L);
        ParticipationRequest request = buildRequest(1L, 2L, RequestStatus.PENDING);
        request.getEvent().setId(77L);
        when(eventRepository.findByIdWithCategoryAndInitiator(1L)).thenReturn(Optional.of(event));
        when(requestRepository.findAllById(List.of(1L))).thenReturn(List.of(request));

        assertThrows(NotFoundException.class, () -> requestService.updateRequestStatuses(1L, 1L,
                EventRequestStatusUpdateRequest.builder()
                        .requestIds(List.of(1L))
                        .status("REJECTED")
                        .build()));
    }

    @Test
    @DisplayName("Пользователь не может отменить чужую заявку")
    void shouldRejectCancelOfForeignRequest() {
        ParticipationRequest request = buildRequest(1L, 2L, RequestStatus.PENDING);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThrows(NotFoundException.class, () -> requestService.cancelRequest(3L, 1L));
    }

    private User buildUser(long id) {
        return User.builder().id(id).name("Пользователь " + id).email("user" + id + "@mail.ru").build();
    }

    private Event buildEvent(EventState state, boolean moderation, long limit) {
        return Event.builder()
                .id(1L)
                .annotation("Аннотация события длиной больше двадцати символов")
                .category(ru.practicum.entity.Category.builder().id(1L).name("Концерты").build())
                .createdOn(LocalDateTime.now().minusDays(1))
                .description("Описание события длиной больше двадцати символов")
                .eventDate(LocalDateTime.now().plusMonths(1))
                .initiator(buildUser(1L))
                .lat(55.75)
                .lon(37.62)
                .paid(true)
                .participantLimit(limit)
                .requestModeration(moderation)
                .state(state)
                .title("Событие")
                .build();
    }

    private ParticipationRequest buildRequest(long id, long requesterId, RequestStatus status) {
        return ParticipationRequest.builder()
                .id(id)
                .created(LocalDateTime.now())
                .event(buildEvent(EventState.PUBLISHED, true, 10L))
                .requester(buildUser(requesterId))
                .status(status)
                .build();
    }
}
