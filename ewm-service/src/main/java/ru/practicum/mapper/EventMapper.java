package ru.practicum.mapper;

import ru.practicum.dto.EventFullDto;
import ru.practicum.dto.EventShortDto;
import ru.practicum.dto.Location;
import ru.practicum.dto.NewEventDto;
import ru.practicum.entity.Category;
import ru.practicum.entity.Event;
import ru.practicum.entity.EventState;
import ru.practicum.entity.User;

import java.time.LocalDateTime;

public final class EventMapper {

    private EventMapper() {
    }

    public static Event toEntity(NewEventDto dto, User initiator, Category category) {
        Location location = dto.getLocation();
        return Event.builder()
                .annotation(dto.getAnnotation())
                .category(category)
                .createdOn(LocalDateTime.now())
                .description(dto.getDescription())
                .eventDate(dto.getEventDate())
                .initiator(initiator)
                .lat(location.getLat())
                .lon(location.getLon())
                .paid(dto.getPaid() != null && dto.getPaid())
                .participantLimit(dto.getParticipantLimit() == null ? 0L : dto.getParticipantLimit())
                .requestModeration(dto.getRequestModeration() == null || dto.getRequestModeration())
                .state(EventState.PENDING)
                .title(dto.getTitle())
                .build();
    }

    public static EventShortDto toShortDto(Event event, Long views, Long confirmedRequests) {
        return EventShortDto.builder()
                .annotation(event.getAnnotation())
                .category(CategoryMapper.toDto(event.getCategory()))
                .confirmedRequests(confirmedRequests)
                .eventDate(event.getEventDate())
                .id(event.getId())
                .initiator(UserMapper.toShortDto(event.getInitiator()))
                .paid(event.getPaid())
                .title(event.getTitle())
                .views(views)
                .build();
    }

    public static EventFullDto toFullDto(Event event, Long views, Long confirmedRequests) {
        return EventFullDto.builder()
                .annotation(event.getAnnotation())
                .category(CategoryMapper.toDto(event.getCategory()))
                .confirmedRequests(confirmedRequests)
                .createdOn(event.getCreatedOn())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .id(event.getId())
                .initiator(UserMapper.toShortDto(event.getInitiator()))
                .location(Location.builder().lat(event.getLat()).lon(event.getLon()).build())
                .paid(event.getPaid())
                .participantLimit(event.getParticipantLimit())
                .publishedOn(event.getPublishedOn())
                .requestModeration(event.getRequestModeration())
                .state(event.getState())
                .title(event.getTitle())
                .views(views)
                .build();
    }
}
