package ru.practicum.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EventFullDto;
import ru.practicum.dto.EventShortDto;
import ru.practicum.dto.NewEventDto;
import ru.practicum.dto.UpdateEventAdminRequest;
import ru.practicum.dto.UpdateEventUserRequest;
import ru.practicum.entity.Category;
import ru.practicum.entity.Event;
import ru.practicum.entity.EventState;
import ru.practicum.entity.ParticipationRequest;
import ru.practicum.entity.RequestStatus;
import ru.practicum.entity.User;
import ru.practicum.exception.BadRequestException;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.EventMapper;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;
import ru.practicum.repository.UserRepository;
import ru.practicum.service.EventService;
import ru.practicum.service.StatsService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private static final int MIN_HOURS_BEFORE_EVENT = 2;

    private static final int MIN_HOURS_BEFORE_PUBLISH = 1;

    private final EventRepository eventRepository;

    private final UserRepository userRepository;

    private final CategoryRepository categoryRepository;

    private final ParticipationRequestRepository requestRepository;

    private final StatsService statsService;

    @Override
    public List<EventShortDto> getPublishedEvents(String text, List<Long> categories, Boolean paid,
                                                  LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                  boolean onlyAvailable, String sort, int from, int size,
                                                  String ip) {
        validateRange(rangeStart, rangeEnd);
        statsService.saveHit("/events", ip);
        LocalDateTime start = rangeStart == null ? LocalDateTime.now() : rangeStart;
        Pageable pageable = buildPageable(from, size);
        Page<Event> events = eventRepository.searchPublishedEvents(text, categories, paid, start, rangeEnd,
                onlyAvailable, pageable);
        List<EventShortDto> result = toShortDtos(events.getContent());
        if ("VIEWS".equalsIgnoreCase(sort)) {
            result.sort(Comparator.comparing(EventShortDto::getViews,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }
        return result;
    }

    @Override
    @Transactional
    public EventFullDto getPublishedEvent(Long eventId, String ip) {
        Event event = getEventOrThrow(eventId);
        if (!EventState.PUBLISHED.equals(event.getState())) {
            throw new NotFoundException("Событие с id=" + eventId + " не опубликовано");
        }
        statsService.saveHit("/events/" + eventId, ip);
        return toFullDto(event);
    }

    @Override
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        Pageable pageable = buildPageable(from, size);
        return toShortDtos(eventRepository.findByInitiatorId(userId, pageable).getContent());
    }

    @Override
    @Transactional
    public EventFullDto addUserEvent(Long userId, NewEventDto dto) {
        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));
        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена"));
        validateEventDateIsInFuture(dto.getEventDate(), MIN_HOURS_BEFORE_EVENT);
        Event saved = eventRepository.save(EventMapper.toEntity(dto, initiator, category));
        log.info("Пользователь {} добавил событие {}", userId, saved.getId());
        return toFullDto(saved);
    }

    @Override
    public EventFullDto getUserEvent(Long userId, Long eventId) {
        Event event = getEventOfUserOrThrow(userId, eventId);
        return toFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest dto) {
        Event event = getEventOfUserOrThrow(userId, eventId);
        if (!EventState.PENDING.equals(event.getState()) && !EventState.CANCELED.equals(event.getState())) {
            throw new ConflictException("Изменять можно только отменённые события или события в ожидании публикации");
        }
        applyUserUpdates(event, dto);
        Event saved = eventRepository.save(event);
        log.info("Пользователь {} изменил событие {}", userId, eventId);
        return toFullDto(saved);
    }

    @Override
    public List<EventFullDto> searchEventsForAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                                   LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                   int from, int size) {
        validateRange(rangeStart, rangeEnd);
        Pageable pageable = buildPageable(from, size);
        Page<Event> events = eventRepository.searchEventsForAdmin(users, states, categories, rangeStart, rangeEnd,
                pageable);
        return toFullDtos(events.getContent());
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest dto) {
        Event event = getEventOrThrow(eventId);
        applyAdminUpdates(event, dto);
        if (dto.getStateAction() != null) {
            applyAdminStateAction(event, dto.getStateAction());
        }
        Event saved = eventRepository.save(event);
        log.info("Администратор изменил событие {}", eventId);
        return toFullDto(saved);
    }

    private void applyUserUpdates(Event event, UpdateEventUserRequest dto) {
        if (dto.getAnnotation() != null) {
            event.setAnnotation(dto.getAnnotation());
        }
        if (dto.getCategory() != null) {
            event.setCategory(getCategoryOrThrow(dto.getCategory()));
        }
        if (dto.getDescription() != null) {
            event.setDescription(dto.getDescription());
        }
        if (dto.getEventDate() != null) {
            validateEventDateIsInFuture(dto.getEventDate(), MIN_HOURS_BEFORE_EVENT);
            event.setEventDate(dto.getEventDate());
        }
        if (dto.getLocation() != null) {
            event.setLat(dto.getLocation().getLat());
            event.setLon(dto.getLocation().getLon());
        }
        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }
        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }
        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
        if (dto.getTitle() != null) {
            event.setTitle(dto.getTitle());
        }
        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case "SEND_TO_REVIEW" -> event.setState(EventState.PENDING);
                case "CANCEL_REVIEW" -> event.setState(EventState.CANCELED);
                default -> throw new BadRequestException("Недопустимое действие над событием");
            }
        }
    }

    private void applyAdminUpdates(Event event, UpdateEventAdminRequest dto) {
        if (dto.getAnnotation() != null) {
            event.setAnnotation(dto.getAnnotation());
        }
        if (dto.getCategory() != null) {
            event.setCategory(getCategoryOrThrow(dto.getCategory()));
        }
        if (dto.getDescription() != null) {
            event.setDescription(dto.getDescription());
        }
        if (dto.getEventDate() != null) {
            if (dto.getEventDate().isBefore(LocalDateTime.now())) {
                throw new BadRequestException("Дата события не может быть в прошлом");
            }
            event.setEventDate(dto.getEventDate());
        }
        if (dto.getLocation() != null) {
            event.setLat(dto.getLocation().getLat());
            event.setLon(dto.getLocation().getLon());
        }
        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }
        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }
        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
        if (dto.getTitle() != null) {
            event.setTitle(dto.getTitle());
        }
    }

    private void applyAdminStateAction(Event event, String stateAction) {
        if ("PUBLISH_EVENT".equals(stateAction)) {
            if (!EventState.PENDING.equals(event.getState())) {
                throw new ConflictException("Публиковать можно только события в ожидании публикации");
            }
            if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_PUBLISH))) {
                throw new ConflictException("Дата события должна быть не раньше чем через час от момента публикации");
            }
            event.setState(EventState.PUBLISHED);
            event.setPublishedOn(LocalDateTime.now());
        } else if ("REJECT_EVENT".equals(stateAction)) {
            if (!EventState.PENDING.equals(event.getState())) {
                throw new ConflictException("Отклонять можно только события в ожидании публикации");
            }
            event.setState(EventState.CANCELED);
        } else {
            throw new BadRequestException("Недопустимое действие над событием");
        }
    }

    private Event getEventOrThrow(Long eventId) {
        return eventRepository.findByIdWithCategoryAndInitiator(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
    }

    private Event getEventOfUserOrThrow(Long userId, Long eventId) {
        Event event = getEventOrThrow(eventId);
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено у пользователя " + userId);
        }
        return event;
    }

    private Category getCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + categoryId + " не найдена"));
    }

    private void validateEventDateIsInFuture(LocalDateTime eventDate, int hours) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(hours))) {
            throw new BadRequestException("Дата события должна быть не раньше чем через "
                    + hours + " час(а) от текущего момента");
        }
    }

    private void validateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("Дата начала диапазона не может быть позже даты конца диапазона");
        }
    }

    private Pageable buildPageable(int from, int size) {
        int safeSize = Math.max(size, 1);
        int safeFrom = Math.max(from, 0);
        return PageRequest.of(safeFrom / safeSize, safeSize, Sort.by(Sort.Direction.ASC, "eventDate"));
    }

    private List<EventShortDto> toShortDtos(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> eventIds = events.stream().map(Event::getId).toList();
        Map<Long, Long> views = statsService.getViewsForEvents(eventIds);
        Map<Long, Long> confirmedRequests = countConfirmedRequests(eventIds);
        return events.stream()
                .map(event -> EventMapper.toShortDto(event,
                        views.getOrDefault(event.getId(), 0L),
                        confirmedRequests.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private List<EventFullDto> toFullDtos(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> eventIds = events.stream().map(Event::getId).toList();
        Map<Long, Long> views = statsService.getViewsForEvents(eventIds);
        Map<Long, Long> confirmedRequests = countConfirmedRequests(eventIds);
        return events.stream()
                .map(event -> EventMapper.toFullDto(event,
                        views.getOrDefault(event.getId(), 0L),
                        confirmedRequests.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private EventFullDto toFullDto(Event event) {
        long confirmedRequests = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
        long views = statsService.getViewsForEvent(event.getId());
        return EventMapper.toFullDto(event, views, confirmedRequests);
    }

    private Map<Long, Long> countConfirmedRequests(List<Long> eventIds) {
        List<ParticipationRequest> requests = requestRepository.findByEventIdInAndStatus(eventIds,
                RequestStatus.CONFIRMED);
        Map<Long, Long> counts = new HashMap<>();
        for (ParticipationRequest request : requests) {
            counts.merge(request.getEvent().getId(), 1L, Long::sum);
        }
        return counts;
    }
}
