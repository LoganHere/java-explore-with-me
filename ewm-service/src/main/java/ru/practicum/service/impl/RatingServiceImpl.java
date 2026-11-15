package ru.practicum.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EventRatingDto;
import ru.practicum.dto.NewRatingDto;
import ru.practicum.dto.RatingDto;
import ru.practicum.dto.UserRatingDto;
import ru.practicum.entity.Event;
import ru.practicum.entity.EventState;
import ru.practicum.entity.Rating;
import ru.practicum.entity.RatingReaction;
import ru.practicum.entity.User;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.RatingMapper;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.RatingRepository;
import ru.practicum.repository.UserRepository;
import ru.practicum.service.RatingService;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingServiceImpl implements RatingService {

    private final RatingRepository ratingRepository;

    private final EventRepository eventRepository;

    private final UserRepository userRepository;

    @Override
    @Transactional
    public RatingDto addRating(Long userId, Long eventId, NewRatingDto dto) {
        User user = getUserOrThrow(userId);
        Event event = getEventOrThrow(eventId);
        checkEventCanBeRated(event, userId);
        if (ratingRepository.existsByEventIdAndUserId(eventId, userId)) {
            throw new ConflictException("Пользователь с id=" + userId
                    + " уже оценил событие с id=" + eventId);
        }
        Rating rating = ratingRepository.save(Rating.builder()
                .event(event)
                .user(user)
                .reaction(dto.getReaction())
                .created(LocalDateTime.now())
                .build());
        applyReactionToEvent(event, dto.getReaction(), 1);
        eventRepository.save(event);
        log.info("Пользователь {} поставил реакцию {} событию {}", userId, dto.getReaction(), eventId);
        return RatingMapper.toDto(rating);
    }

    @Override
    @Transactional
    public RatingDto updateRating(Long userId, Long eventId, NewRatingDto dto) {
        Rating rating = getRatingOrThrow(userId, eventId);
        RatingReaction previous = rating.getReaction();
        if (!Objects.equals(previous, dto.getReaction())) {
            Event event = rating.getEvent();
            applyReactionToEvent(event, previous, -1);
            applyReactionToEvent(event, dto.getReaction(), 1);
            eventRepository.save(event);
            rating.setReaction(dto.getReaction());
            rating = ratingRepository.save(rating);
        }
        log.info("Пользователь {} изменил реакцию на событие {} на {}", userId, eventId, dto.getReaction());
        return RatingMapper.toDto(rating);
    }

    @Override
    @Transactional
    public void deleteRating(Long userId, Long eventId) {
        Rating rating = getRatingOrThrow(userId, eventId);
        Event event = rating.getEvent();
        applyReactionToEvent(event, rating.getReaction(), -1);
        eventRepository.save(event);
        ratingRepository.delete(rating);
        log.info("Пользователь {} отменил свою реакцию на событие {}", userId, eventId);
    }

    @Override
    public RatingDto getUserRating(Long userId, Long eventId) {
        return RatingMapper.toDto(getRatingOrThrow(userId, eventId));
    }

    @Override
    public EventRatingDto getEventRating(Long eventId) {
        Event event = getEventOrThrow(eventId);
        if (!EventState.PUBLISHED.equals(event.getState())) {
            throw new NotFoundException("Событие с id=" + eventId + " не опубликовано");
        }
        return RatingMapper.toEventRatingDto(event);
    }

    @Override
    public UserRatingDto getAuthorRating(Long userId) {
        User user = getUserOrThrow(userId);
        Long likes = eventRepository.sumLikesByInitiatorId(userId);
        Long dislikes = eventRepository.sumDislikesByInitiatorId(userId);
        long events = eventRepository.countByInitiatorId(userId);
        return RatingMapper.toUserRatingDto(user, likes, dislikes, events);
    }

    private void checkEventCanBeRated(Event event, Long userId) {
        if (!EventState.PUBLISHED.equals(event.getState())) {
            throw new ConflictException("Оценивать можно только опубликованные события");
        }
        if (Objects.equals(event.getInitiator().getId(), userId)) {
            throw new ConflictException("Инициатор события не может оценивать собственное событие");
        }
    }

    private void applyReactionToEvent(Event event, RatingReaction reaction, int delta) {
        if (RatingReaction.LIKE.equals(reaction)) {
            event.setLikes(Math.max(0L, RatingMapper.valueOrZero(event.getLikes()) + delta));
        } else {
            event.setDislikes(Math.max(0L, RatingMapper.valueOrZero(event.getDislikes()) + delta));
        }
    }

    private Rating getRatingOrThrow(Long userId, Long eventId) {
        return ratingRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId
                        + " не оценивал событие с id=" + eventId));
    }

    private Event getEventOrThrow(Long eventId) {
        return eventRepository.findByIdWithCategoryAndInitiator(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));
    }
}
