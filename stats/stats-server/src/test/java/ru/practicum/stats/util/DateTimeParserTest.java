package ru.practicum.stats.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Тесты утилиты разбора даты и времени")
class DateTimeParserTest {

    @Test
    @DisplayName("Разбор даты и времени в формате спецификации")
    void shouldParseSpecFormat() {
        LocalDateTime expected = LocalDateTime.of(2026, 11, 4, 12, 30, 15);

        LocalDateTime actual = DateTimeParser.parse("2026-11-04 12:30:15");

        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Разбор даты и времени в ISO-формате")
    void shouldParseIsoFormat() {
        LocalDateTime expected = LocalDateTime.of(2026, 11, 4, 12, 30, 15);

        LocalDateTime actual = DateTimeParser.parse("2026-11-04T12:30:15");

        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Разбор строки, не являющейся датой, завершается ошибкой")
    void shouldThrowWhenValueIsNotDate() {
        assertThrows(DateTimeParseException.class, () -> DateTimeParser.parse("не дата"));
        assertThrows(DateTimeParseException.class, () -> DateTimeParser.parse(null));
    }
}
