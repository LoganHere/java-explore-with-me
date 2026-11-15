package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.dto.EventRatingDto;
import ru.practicum.dto.UserRatingDto;
import ru.practicum.service.RatingService;

@Slf4j
@Validated
@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
public class PublicRatingController {

    private final RatingService ratingService;

    @GetMapping("/events/{eventId}")
    public ResponseEntity<EventRatingDto> getEventRating(@PathVariable long eventId) {
        EventRatingDto rating = ratingService.getEventRating(eventId);
        log.info("Возвращён рейтинг события {}", eventId);
        return ResponseEntity.ok(rating);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserRatingDto> getAuthorRating(@PathVariable long userId) {
        UserRatingDto rating = ratingService.getAuthorRating(userId);
        log.info("Возвращён рейтинг автора {}", userId);
        return ResponseEntity.ok(rating);
    }
}
