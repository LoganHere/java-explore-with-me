package ru.practicum.mapper;

import ru.practicum.dto.EventRatingDto;
import ru.practicum.dto.RatingDto;
import ru.practicum.dto.UserRatingDto;
import ru.practicum.entity.Event;
import ru.practicum.entity.Rating;
import ru.practicum.entity.User;

public final class RatingMapper {

    private RatingMapper() {
    }

    public static RatingDto toDto(Rating rating) {
        return RatingDto.builder()
                .id(rating.getId())
                .event(rating.getEvent().getId())
                .user(rating.getUser().getId())
                .reaction(rating.getReaction())
                .created(rating.getCreated())
                .build();
    }

    public static EventRatingDto toEventRatingDto(Event event) {
        return EventRatingDto.builder()
                .eventId(event.getId())
                .likes(valueOrZero(event.getLikes()))
                .dislikes(valueOrZero(event.getDislikes()))
                .rating(score(event.getLikes(), event.getDislikes()))
                .build();
    }

    public static UserRatingDto toUserRatingDto(User user, Long likes, Long dislikes, Long events) {
        return UserRatingDto.builder()
                .userId(user.getId())
                .name(user.getName())
                .likes(valueOrZero(likes))
                .dislikes(valueOrZero(dislikes))
                .rating(score(likes, dislikes))
                .events(valueOrZero(events))
                .build();
    }

    public static long score(Long likes, Long dislikes) {
        return valueOrZero(likes) - valueOrZero(dislikes);
    }

    public static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
