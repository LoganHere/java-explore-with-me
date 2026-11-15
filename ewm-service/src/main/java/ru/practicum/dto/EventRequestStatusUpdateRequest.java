package ru.practicum.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class EventRequestStatusUpdateRequest {

    @NotEmpty(message = "Список идентификаторов заявок не может быть пустым")
    private List<Long> requestIds;

    @Pattern(regexp = "CONFIRMED|REJECTED", message = "Недопустимый статус заявки")
    private String status;
}
