package ru.practicum.mapper;

import ru.practicum.dto.CategoryDto;
import ru.practicum.entity.Category;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static CategoryDto toDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .build();
    }
}
