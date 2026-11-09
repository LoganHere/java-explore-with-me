package ru.practicum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class NewUserRequest {

    private static final String EMAIL_PATTERN =
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]{1,64}"
                    + "@[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$";

    @NotBlank(message = "Почтовый адрес не может быть пустым")
    @Size(min = 6, max = 254, message = "Почтовый адрес должен содержать от 6 до 254 символов")
    @Pattern(regexp = EMAIL_PATTERN, message = "Почтовый адрес указан в неверном формате")
    private String email;

    @NotBlank(message = "Имя не может быть пустым")
    @Size(min = 2, max = 250, message = "Имя должно содержать от 2 до 250 символов")
    private String name;
}
