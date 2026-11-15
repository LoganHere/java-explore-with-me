package ru.practicum.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.practicum.dto.ApiError;

import java.time.format.DateTimeParseException;
import jakarta.validation.ConstraintViolationException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.toList());
        log.warn("Ошибка валидации: {}", errors);
        return buildResponse(HttpStatus.BAD_REQUEST, "Ошибка валидации запроса", errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException exception) {
        String message = String.format("Обязательный параметр %s отсутствует", exception.getParameterName());
        log.warn("Некорректный запрос: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message, Collections.emptyList());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        List<String> errors = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toList());
        log.warn("Ошибка валидации параметров: {}", errors);
        return buildResponse(HttpStatus.BAD_REQUEST, "Ошибка валидации параметров запроса", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException exception) {
        log.warn("Некорректное тело запроса: {}", exception.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Тело запроса содержит некорректные данные",
                Collections.emptyList());
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiError> handleDateTimeParse(DateTimeParseException exception) {
        log.warn("Некорректный формат даты и времени: {}", exception.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Неверный формат даты и времени", Collections.emptyList());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String message = String.format("Параметр %s имеет неверный формат", exception.getName());
        log.warn("Некорректный запрос: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message, Collections.emptyList());
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException exception) {
        log.warn("Некорректный запрос: {}", exception.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), Collections.emptyList());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException exception) {
        log.warn("Объект не найден: {}", exception.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), Collections.emptyList());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException exception) {
        log.warn("Конфликт данных: {}", exception.getMessage());
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), Collections.emptyList());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Нарушение целостности данных: {}", exception.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "Нарушены ограничения целостности данных",
                Collections.emptyList());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception exception) {
        log.error("Внутренняя ошибка сервера: ", exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(),
                List.of(exception.getClass().getSimpleName()));
    }

    private String describeFieldError(FieldError error) {
        String field = Objects.toString(error.getField(), "unknown");
        String message = Objects.toString(error.getDefaultMessage(), "некорректное значение");
        return String.format("%s: %s", field, message);
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String message, List<String> errors) {
        return ResponseEntity.status(status).body(ApiError.of(status, message, errors));
    }
}
