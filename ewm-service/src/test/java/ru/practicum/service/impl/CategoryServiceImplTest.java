package ru.practicum.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.practicum.dto.NewCategoryDto;
import ru.practicum.entity.Category;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Юнит-тесты сервиса категорий")
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    @DisplayName("Нельзя добавить категорию с занятым названием")
    void shouldRejectDuplicateName() {
        when(categoryRepository.existsByName("Концерты")).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> categoryService.createCategory(NewCategoryDto.builder().name("Концерты").build()));

        assertEquals("Категория с названием Концерты уже существует", exception.getMessage());
        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Нельзя изменить название категории на занятое")
    void shouldRejectRenameToDuplicate() {
        Category category = Category.builder().id(1L).name("Концерты").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByName("Выставки")).thenReturn(true);

        assertThrows(ConflictException.class, () -> categoryService.updateCategory(1L,
                ru.practicum.dto.CategoryDto.builder().id(1L).name("Выставки").build()));
    }

    @Test
    @DisplayName("Категорию со связанными событиями нельзя удалить")
    void shouldRejectDeleteOfUsedCategory() {
        Category category = Category.builder().id(1L).name("Концерты").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(eventRepository.countByCategoryId(1L)).thenReturn(2L);

        assertThrows(ConflictException.class, () -> categoryService.deleteCategory(1L));
        verify(categoryRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Удаление несуществующей категории завершается ошибкой 404")
    void shouldRejectDeleteOfUnknownCategory() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> categoryService.deleteCategory(99L));
    }
}
