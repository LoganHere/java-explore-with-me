package ru.practicum.service;

import ru.practicum.dto.EventRatingDto;
import ru.practicum.dto.NewRatingDto;
import ru.practicum.dto.RatingDto;
import ru.practicum.dto.UserRatingDto;

public interface RatingService {

    RatingDto addRating(Long userId, Long eventId, NewRatingDto dto);

    RatingDto updateRating(Long userId, Long eventId, NewRatingDto dto);

    void deleteRating(Long userId, Long eventId);

    RatingDto getUserRating(Long userId, Long eventId);

    EventRatingDto getEventRating(Long eventId);

    UserRatingDto getAuthorRating(Long userId);
}
