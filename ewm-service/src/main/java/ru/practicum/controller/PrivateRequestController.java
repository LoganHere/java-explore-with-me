package ru.practicum.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.dto.EventRequestStatusUpdateRequest;
import ru.practicum.dto.EventRequestStatusUpdateResult;
import ru.practicum.dto.ParticipationRequestDto;
import ru.practicum.service.RequestService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/users/{userId}")
@RequiredArgsConstructor
public class PrivateRequestController {

    private final RequestService requestService;

    @GetMapping("/requests")
    public ResponseEntity<List<ParticipationRequestDto>> getUserRequests(@PathVariable long userId) {
        List<ParticipationRequestDto> requests = requestService.getUserRequests(userId);
        log.info("Пользователю {} возвращено {} заявок", userId, requests.size());
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/requests")
    public ResponseEntity<ParticipationRequestDto> addRequest(@PathVariable long userId,
                                                              @RequestParam long eventId) {
        ParticipationRequestDto created = requestService.addRequest(userId, eventId);
        log.info("Пользователь {} отправил заявку на участие в событии {}", userId, eventId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/requests/{requestId}/cancel")
    public ResponseEntity<ParticipationRequestDto> cancelRequest(@PathVariable long userId,
                                                                 @PathVariable long requestId) {
        ParticipationRequestDto cancelled = requestService.cancelRequest(userId, requestId);
        log.info("Пользователь {} отменил заявку {}", userId, requestId);
        return ResponseEntity.ok(cancelled);
    }

    @GetMapping("/events/{eventId}/requests")
    public ResponseEntity<List<ParticipationRequestDto>> getEventRequests(@PathVariable long userId,
                                                                          @PathVariable long eventId) {
        List<ParticipationRequestDto> requests = requestService.getEventRequests(userId, eventId);
        log.info("Пользователю {} возвращено {} заявок на событие {}", userId, requests.size(), eventId);
        return ResponseEntity.ok(requests);
    }

    @PatchMapping("/events/{eventId}/requests")
    public ResponseEntity<EventRequestStatusUpdateResult> updateRequestStatuses(
            @PathVariable long userId,
            @PathVariable long eventId,
            @Valid @RequestBody EventRequestStatusUpdateRequest dto) {
        EventRequestStatusUpdateResult result = requestService.updateRequestStatuses(userId, eventId, dto);
        log.info("Пользователь {} изменил статус заявок на участие в событии {}", userId, eventId);
        return ResponseEntity.ok(result);
    }
}
