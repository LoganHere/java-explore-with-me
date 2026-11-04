package ru.practicum.stats.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException exception) {
        String message = String.format("Обязательный параметр %s отсутствует", exception.getParameterName());
        log.warn("Некорректный запрос: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message, Collections.emptyList());
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ErrorResponse> handleDateTimeParse(DateTimeParseException exception) {
        log.warn("Некорректный формат даты и времени: {}", exception.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Неверный формат даты и времени", Collections.emptyList());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String message = String.format("Параметр %s имеет неверный формат", exception.getName());
        log.warn("Некорректный запрос: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message, Collections.emptyList());
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException exception) {
        log.warn("Некорректный запрос: {}", exception.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), Collections.emptyList());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception exception) {
        log.error("Внутренняя ошибка сервера: ", exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(),
                List.of(exception.getClass().getSimpleName()));
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, List<String> errors) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, errors));
    }
}
