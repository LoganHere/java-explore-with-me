package ru.practicum.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EventRequestStatusUpdateRequest;
import ru.practicum.dto.EventRequestStatusUpdateResult;
import ru.practicum.dto.ParticipationRequestDto;
import ru.practicum.entity.Event;
import ru.practicum.entity.EventState;
import ru.practicum.entity.ParticipationRequest;
import ru.practicum.entity.RequestStatus;
import ru.practicum.entity.User;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;
import ru.practicum.repository.UserRepository;
import ru.practicum.service.RequestService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestServiceImpl implements RequestService {

    private final ParticipationRequestRepository requestRepository;

    private final EventRepository eventRepository;

    private final UserRepository userRepository;

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        return requestRepository.findByRequesterId(userId).stream()
                .map(RequestMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public ParticipationRequestDto addRequest(Long userId, Long eventId) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));
        Event event = eventRepository.findByIdWithCategoryAndInitiator(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
        if (Objects.equals(event.getInitiator().getId(), userId)) {
            throw new ConflictException("Инициатор события не может добавить заявку на участие в собственном событии");
        }
        if (!EventState.PUBLISHED.equals(event.getState())) {
            throw new ConflictException("Нельзя участвовать в неопубликованном событии");
        }
        if (requestRepository.findByEventIdAndRequesterId(eventId, userId) != null) {
            throw new ConflictException("Заявка на участие в этом событии уже отправлена");
        }
        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() > 0 && confirmed >= event.getParticipantLimit()) {
            throw new ConflictException("Достигнут лимит участников события");
        }
        RequestStatus status = resolveStatusForNewRequest(event);
        ParticipationRequest saved = requestRepository.save(ParticipationRequest.builder()
                .created(LocalDateTime.now())
                .event(event)
                .requester(requester)
                .status(status)
                .build());
        log.info("Пользователь {} отправил заявку на участие в событии {}", userId, eventId);
        return RequestMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        ParticipationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Заявка с id=" + requestId + " не найдена"));
        if (!Objects.equals(request.getRequester().getId(), userId)) {
            throw new NotFoundException("Заявка с id=" + requestId + " не найдена у пользователя " + userId);
        }
        request.setStatus(RequestStatus.CANCELED);
        ParticipationRequest saved = requestRepository.save(request);
        log.info("Пользователь {} отменил заявку {}", userId, requestId);
        return RequestMapper.toDto(saved);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        Event event = getEventOfUserOrThrow(userId, eventId);
        return requestRepository.findByEventId(event.getId()).stream()
                .map(RequestMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatuses(Long userId, Long eventId,
                                                                EventRequestStatusUpdateRequest dto) {
        Event event = getEventOfUserOrThrow(userId, eventId);
        List<Long> requestIds = dto.getRequestIds();
        List<ParticipationRequest> requests = requestRepository.findAllById(requestIds);
        if (requests.size() != requestIds.size()) {
            throw new NotFoundException("Одна или несколько заявок не найдены");
        }
        for (ParticipationRequest request : requests) {
            if (!Objects.equals(request.getEvent().getId(), eventId)) {
                throw new NotFoundException("Заявка с id=" + request.getId()
                        + " не относится к событию " + eventId);
            }
            if (!RequestStatus.PENDING.equals(request.getStatus())) {
                throw new ConflictException("Изменять статус можно только у заявок в ожидании подтверждения");
            }
        }
        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();
        if ("CONFIRMED".equals(dto.getStatus())) {
            long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            for (ParticipationRequest request : requests) {
                if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
                    throw new ConflictException("Достигнут лимит участников события");
                }
                request.setStatus(RequestStatus.CONFIRMED);
                confirmedCount++;
                confirmed.add(RequestMapper.toDto(requestRepository.save(request)));
            }
        } else if ("REJECTED".equals(dto.getStatus())) {
            for (ParticipationRequest request : requests) {
                request.setStatus(RequestStatus.REJECTED);
                rejected.add(RequestMapper.toDto(requestRepository.save(request)));
            }
        } else {
            throw new ConflictException("Недопустимый статус заявки");
        }
        log.info("Пользователь {} изменил статус {} заявок на участие в событии {}", userId,
                requests.size(), eventId);
        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed)
                .rejectedRequests(rejected)
                .build();
    }

    private RequestStatus resolveStatusForNewRequest(Event event) {
        if (event.getParticipantLimit() == 0 || !event.getRequestModeration()) {
            return RequestStatus.CONFIRMED;
        }
        return RequestStatus.PENDING;
    }

    private Event getEventOfUserOrThrow(Long userId, Long eventId) {
        Event event = eventRepository.findByIdWithCategoryAndInitiator(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено у пользователя " + userId);
        }
        return event;
    }
}
