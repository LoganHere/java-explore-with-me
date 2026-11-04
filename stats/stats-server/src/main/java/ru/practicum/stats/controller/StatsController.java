package ru.practicum.stats.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.stats.service.StatsService;
import ru.practicum.stats.util.DateTimeParser;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class StatsController {

    private final StatsService service;

    @PostMapping("/hit")
    public ResponseEntity<EndpointHit> saveHit(@Valid @RequestBody EndpointHit hit) {
        log.info("Сохранение информации о запросе к эндпоинту {}", hit.getUri());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saveHit(hit));
    }

    @GetMapping("/stats")
    public ResponseEntity<List<ViewStats>> getStats(@RequestParam(name = "start") String start,
                                                    @RequestParam(name = "end") String end,
                                                    @RequestParam(name = "uris", required = false) List<String> uris,
                                                    @RequestParam(name = "unique",
                                                            required = false,
                                                            defaultValue = "false") boolean unique) {
        LocalDateTime startTime = DateTimeParser.parse(start);
        LocalDateTime endTime = DateTimeParser.parse(end);
        List<ViewStats> stats = service.getStats(startTime, endTime, uris, unique);
        log.info("Возвращено {} записей статистики", stats.size());
        return ResponseEntity.ok(stats);
    }
}
