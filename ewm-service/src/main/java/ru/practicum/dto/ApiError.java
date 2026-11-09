package ru.practicum.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

    private List<String> errors;

    private String message;

    private String reason;

    private String status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    public static ApiError of(HttpStatus status, String message, List<String> errors) {
        return ApiError.builder()
                .status(status.name())
                .reason(status.getReasonPhrase())
                .message(message)
                .errors(errors == null || errors.isEmpty() ? List.of() : errors)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
