package ru.practicum.stats.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.practicum.stats.entity.EndpointHitEntity;
import ru.practicum.stats.exception.BadRequestException;
import ru.practicum.stats.repository.StatsRepository;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Юнит-тесты сервиса статистики")
class StatsServiceImplTest {

    @Mock
    private StatsRepository repository;

    @InjectMocks
    private StatsServiceImpl service;

    @Captor
    private ArgumentCaptor<EndpointHitEntity> entityCaptor;

    @Test
    @DisplayName("При сохранении без времени запроса оно заполняется текущим моментом")
    void shouldSetTimestampWhenSavingHitWithoutTimestamp() {
        EndpointHit hit = EndpointHit.builder()
                .app("ewm-main-service")
                .uri("/events")
                .ip("192.168.0.1")
                .build();
        when(repository.save(any(EndpointHitEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EndpointHit saved = service.saveHit(hit);

        assertNotNull(saved.getTimestamp());
        verify(repository).save(entityCaptor.capture());
        assertEquals("/events", entityCaptor.getValue().getUri());
    }

    @Test
    @DisplayName("При сохранении переданное время запроса не изменяется")
    void shouldKeepTimestampWhenSavingHit() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 11, 4, 12, 0);
        EndpointHit hit = EndpointHit.builder()
                .app("ewm-main-service")
                .uri("/events")
                .ip("192.168.0.1")
                .timestamp(timestamp)
                .build();
        when(repository.save(any(EndpointHitEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EndpointHit saved = service.saveHit(hit);

        assertEquals(timestamp, saved.getTimestamp());
    }

    @Test
    @DisplayName("Получение статистики с началом периода позже конца завершается ошибкой 400")
    void shouldThrowWhenStartAfterEnd() {
        LocalDateTime start = LocalDateTime.of(2035, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2020, 1, 1, 0, 0);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.getStats(start, end, List.of("/events"), true));

        assertEquals("Дата начала периода не может быть позже даты конца периода", exception.getMessage());
        verify(repository, never()).findAllStats(any(), any());
    }

    @Test
    @DisplayName("Получение статистики без URI вызывает запрос по всем URI")
    void shouldCallFindAllWhenUrisEmpty() {
        LocalDateTime start = LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2035, 1, 1, 0, 0);
        when(repository.findAllStats(start, end)).thenReturn(List.of(new ViewStats("ewm", "/events", 3L)));

        List<ViewStats> stats = service.getStats(start, end, null, false);

        assertEquals(1, stats.size());
        assertEquals(3L, stats.get(0).getHits());
        verify(repository, never()).findStatsByUris(any(), any(), any());
    }

    @Test
    @DisplayName("Получение уникальной статистики по URI вызывает запрос уникальной статистики")
    void shouldCallUniqueQueryWhenUniqueTrue() {
        LocalDateTime start = LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2035, 1, 1, 0, 0);
        List<String> uris = List.of("/events");
        when(repository.findUniqueStatsByUris(start, end, uris))
                .thenReturn(List.of(new ViewStats("ewm", "/events", 1L)));

        List<ViewStats> stats = service.getStats(start, end, uris, true);

        assertEquals(1, stats.size());
        assertEquals(1L, stats.get(0).getHits());
        verify(repository, never()).findStatsByUris(any(), any(), any());
    }
}
