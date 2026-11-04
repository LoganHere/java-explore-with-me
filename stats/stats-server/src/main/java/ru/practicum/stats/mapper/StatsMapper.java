package ru.practicum.stats.mapper;

import ru.practicum.stats.entity.EndpointHitEntity;
import ru.practicum.statsdto.EndpointHit;

public final class StatsMapper {

    private StatsMapper() {
    }

    public static EndpointHitEntity toEntity(EndpointHit hit) {
        return EndpointHitEntity.builder()
                .app(hit.getApp())
                .uri(hit.getUri())
                .ip(hit.getIp())
                .timestamp(hit.getTimestamp())
                .build();
    }

    public static EndpointHit toDto(EndpointHitEntity entity) {
        return EndpointHit.builder()
                .id(entity.getId())
                .app(entity.getApp())
                .uri(entity.getUri())
                .ip(entity.getIp())
                .timestamp(entity.getTimestamp())
                .build();
    }
}
