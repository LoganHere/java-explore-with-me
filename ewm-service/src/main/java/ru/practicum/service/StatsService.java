package ru.practicum.service;

import java.util.List;
import java.util.Map;

public interface StatsService {

    void saveHit(String uri, String ip);

    Map<Long, Long> getViewsForEvents(List<Long> eventIds);

    long getViewsForEvent(Long eventId);
}
