package ru.practicum.stats.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.stats.entity.EndpointHitEntity;
import ru.practicum.stats.exception.BadRequestException;
import ru.practicum.stats.mapper.StatsMapper;
import ru.practicum.stats.repository.StatsRepository;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

    private final StatsRepository repository;

    @Override
    @Transactional
    public EndpointHit saveHit(EndpointHit hit) {
        if (hit.getTimestamp() == null) {
            hit.setTimestamp(LocalDateTime.now());
        }
        EndpointHitEntity saved = repository.save(StatsMapper.toEntity(hit));
        log.debug("Сохранено обращение к эндпоинту {} с ip {}", saved.getUri(), saved.getIp());
        return StatsMapper.toDto(saved);
    }

    @Override
    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        if (start == null || end == null) {
            throw new BadRequestException("Даты начала и конца периода должны быть указаны");
        }
        if (start.isAfter(end)) {
            throw new BadRequestException("Дата начала периода не может быть позже даты конца периода");
        }
        boolean hasUris = uris != null && !uris.isEmpty();
        if (!hasUris) {
            return unique ? repository.findAllUniqueStats(start, end) : repository.findAllStats(start, end);
        }
        return unique ? repository.findUniqueStatsByUris(start, end, uris) : repository.findStatsByUris(start, end, uris);
    }
}
