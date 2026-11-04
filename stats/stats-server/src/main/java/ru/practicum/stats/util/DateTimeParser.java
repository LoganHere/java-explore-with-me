package ru.practicum.stats.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DateTimeParser {

    private static final DateTimeFormatter SPEC_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateTimeParser() {
    }

    public static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            throw new DateTimeParseException("Пустое значение даты и времени", value == null ? "" : value, 0);
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed, SPEC_FORMAT);
        } catch (DateTimeParseException exception) {
            return LocalDateTime.parse(trimmed);
        }
    }
}
