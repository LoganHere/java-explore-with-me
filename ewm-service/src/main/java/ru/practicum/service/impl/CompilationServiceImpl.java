package ru.practicum.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.CompilationDto;
import ru.practicum.dto.EventShortDto;
import ru.practicum.dto.NewCompilationDto;
import ru.practicum.dto.UpdateCompilationRequest;
import ru.practicum.entity.Compilation;
import ru.practicum.entity.Event;
import ru.practicum.entity.ParticipationRequest;
import ru.practicum.entity.RequestStatus;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.EventMapper;
import ru.practicum.repository.CompilationRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;
import ru.practicum.service.CompilationService;
import ru.practicum.service.StatsService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;

    private final EventRepository eventRepository;

    private final ParticipationRequestRepository requestRepository;

    private final StatsService statsService;

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        Pageable pageable = PageRequest.of(from / Math.max(size, 1), Math.max(size, 1));
        List<Compilation> compilations = pinned == null
                ? compilationRepository.findAll(pageable).getContent()
                : compilationRepository.findByPinned(pinned, pageable).getContent();
        return compilations.stream().map(this::toDto).toList();
    }

    @Override
    public CompilationDto getCompilation(Long compId) {
        Compilation compilation = getCompilationOrThrow(compId);
        return toDto(compilation);
    }

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto dto) {
        Set<Event> events = resolveEvents(dto.getEvents());
        Compilation compilation = Compilation.builder()
                .pinned(dto.getPinned() != null && dto.getPinned())
                .title(dto.getTitle())
                .events(events)
                .build();
        Compilation saved = compilationRepository.save(compilation);
        log.info("Добавлена подборка {} с {} событиями", saved.getId(), events.size());
        return toDto(saved);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest dto) {
        Compilation compilation = getCompilationOrThrow(compId);
        if (dto.getPinned() != null) {
            compilation.setPinned(dto.getPinned());
        }
        if (dto.getTitle() != null) {
            compilation.setTitle(dto.getTitle());
        }
        if (dto.getEvents() != null) {
            compilation.setEvents(resolveEvents(dto.getEvents()));
        }
        Compilation saved = compilationRepository.save(compilation);
        log.info("Изменена подборка {}", saved.getId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        Compilation compilation = getCompilationOrThrow(compId);
        compilationRepository.delete(compilation);
        log.info("Удалена подборка {}", compId);
    }

    private Compilation getCompilationOrThrow(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));
    }

    private Set<Event> resolveEvents(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Event> events = eventRepository.findAllById(eventIds);
        if (events.size() != new HashSet<>(eventIds).size()) {
            throw new NotFoundException("Одно или несколько событий подборки не найдены");
        }
        return new HashSet<>(events);
    }

    private CompilationDto toDto(Compilation compilation) {
        List<Event> events = compilation.getEvents().stream().toList();
        List<EventShortDto> eventDtos = toShortDtos(events);
        return CompilationDto.builder()
                .events(eventDtos)
                .id(compilation.getId())
                .pinned(compilation.getPinned())
                .title(compilation.getTitle())
                .build();
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
