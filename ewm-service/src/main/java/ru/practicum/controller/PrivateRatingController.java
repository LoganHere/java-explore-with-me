package ru.practicum.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.dto.NewRatingDto;
import ru.practicum.dto.RatingDto;
import ru.practicum.service.RatingService;

@Slf4j
@Validated
@RestController
@RequestMapping("/users/{userId}/events/{eventId}/rating")
@RequiredArgsConstructor
public class PrivateRatingController {

    private final RatingService ratingService;

    @PostMapping
    public ResponseEntity<RatingDto> addRating(@PathVariable long userId,
                                               @PathVariable long eventId,
                                               @Valid @RequestBody NewRatingDto dto) {
        RatingDto rating = ratingService.addRating(userId, eventId, dto);
        log.info("Пользователь {} поставил реакцию {} событию {}", userId, rating.getReaction(), eventId);
        return ResponseEntity.status(HttpStatus.CREATED).body(rating);
    }

    @PatchMapping
    public ResponseEntity<RatingDto> updateRating(@PathVariable long userId,
                                                  @PathVariable long eventId,
                                                  @Valid @RequestBody NewRatingDto dto) {
        RatingDto rating = ratingService.updateRating(userId, eventId, dto);
        log.info("Пользователь {} изменил реакцию на событие {} на {}", userId, eventId, rating.getReaction());
        return ResponseEntity.ok(rating);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteRating(@PathVariable long userId, @PathVariable long eventId) {
        ratingService.deleteRating(userId, eventId);
        log.info("Пользователь {} удалил реакцию на событие {}", userId, eventId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<RatingDto> getUserRating(@PathVariable long userId, @PathVariable long eventId) {
        RatingDto rating = ratingService.getUserRating(userId, eventId);
        log.info("Возвращена реакция пользователя {} на событие {}", userId, eventId);
        return ResponseEntity.ok(rating);
    }
}
