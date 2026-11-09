package ru.practicum.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.dto.EventFullDto;
import ru.practicum.dto.EventShortDto;
import ru.practicum.dto.NewEventDto;
import ru.practicum.dto.UpdateEventUserRequest;
import ru.practicum.service.EventService;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/users/{userId}/events")
@RequiredArgsConstructor
public class PrivateEventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventShortDto>> getUserEvents(
            @PathVariable long userId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @PositiveOrZero int size) {
        List<EventShortDto> events = eventService.getUserEvents(userId, from, size);
        log.info("Пользователю {} возвращено {} событий", userId, events.size());
        return ResponseEntity.ok(events);
    }

    @PostMapping
    public ResponseEntity<EventFullDto> addUserEvent(@PathVariable long userId,
                                                     @Valid @RequestBody NewEventDto dto) {
        EventFullDto created = eventService.addUserEvent(userId, dto);
        log.info("Пользователь {} добавил событие {}", userId, created.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventFullDto> getUserEvent(@PathVariable long userId, @PathVariable long eventId) {
        EventFullDto event = eventService.getUserEvent(userId, eventId);
        log.info("Пользователю {} возвращено событие {}", userId, eventId);
        return ResponseEntity.ok(event);
    }

    @PatchMapping("/{eventId}")
    public ResponseEntity<EventFullDto> updateUserEvent(@PathVariable long userId, @PathVariable long eventId,
                                                        @Valid @RequestBody UpdateEventUserRequest dto) {
        EventFullDto updated = eventService.updateUserEvent(userId, eventId, dto);
        log.info("Пользователь {} изменил событие {}", userId, eventId);
        return ResponseEntity.ok(updated);
    }
}
