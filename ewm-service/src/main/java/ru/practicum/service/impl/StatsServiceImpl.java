package ru.practicum.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.service.StatsService;
import ru.practicum.statsclient.StatsClient;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final LocalDateTime STATS_START = LocalDateTime.of(2020, 1, 1, 0, 0);

    private final StatsClient client;

    @Value("${spring.application.name:ewm-service}")
    private String appName;

    @Override
    public void saveHit(String uri, String ip) {
        try {
            client.saveHit(appName, uri, ip);
        } catch (RuntimeException exception) {
            log.warn("Не удалось сохранить обращение к эндпоинту {}: {}", uri, exception.getMessage());
        }
    }

    @Override
    public Map<Long, Long> getViewsForEvents(List<Long> eventIds) {
        Map<Long, Long> views = new HashMap<>();
        if (eventIds == null || eventIds.isEmpty()) {
            return views;
        }
        List<String> uris = eventIds.stream()
                .map(eventId -> "/events/" + eventId)
                .toList();
        Map<String, Long> viewsByUri = getViewsByUri(uris);
        for (Long eventId : eventIds) {
            views.put(eventId, viewsByUri.getOrDefault("/events/" + eventId, 0L));
        }
        return views;
    }

    @Override
    public long getViewsForEvent(Long eventId) {
        return getViewsForEvents(List.of(eventId)).getOrDefault(eventId, 0L);
    }

    private Map<String, Long> getViewsByUri(List<String> uris) {
        List<ViewStats> stats = client.getStats(STATS_START, LocalDateTime.now().plusYears(1), uris, true);
        Map<String, Long> viewsByUri = new HashMap<>();
        for (ViewStats stat : stats) {
            viewsByUri.put(stat.getUri(), stat.getHits());
        }
        return viewsByUri;
    }
}
