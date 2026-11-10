package ru.practicum.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.CategoryDto;
import ru.practicum.dto.NewCategoryDto;
import ru.practicum.entity.Category;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.CategoryMapper;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.service.CategoryService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    private final EventRepository eventRepository;

    @Override
    public List<CategoryDto> getCategories(int from, int size) {
        Pageable pageable = PageRequest.of(from / Math.max(size, 1), Math.max(size, 1));
        return categoryRepository.findAll(pageable).getContent().stream()
                .map(CategoryMapper::toDto)
                .toList();
    }

    @Override
    public CategoryDto getCategory(Long catId) {
        Category category = getCategoryOrThrow(catId);
        return CategoryMapper.toDto(category);
    }

    @Override
    @Transactional
    public CategoryDto createCategory(NewCategoryDto dto) {
        checkNameIsFree(dto.getName());
        Category saved = categoryRepository.save(Category.builder().name(dto.getName()).build());
        log.info("Добавлена категория {}", saved.getId());
        return CategoryMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto dto) {
        Category category = getCategoryOrThrow(catId);
        if (!category.getName().equals(dto.getName())) {
            checkNameIsFree(dto.getName());
            category.setName(dto.getName());
        }
        Category saved = categoryRepository.save(category);
        log.info("Изменена категория {}", saved.getId());
        return CategoryMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteCategory(Long catId) {
        Category category = getCategoryOrThrow(catId);
        if (eventRepository.countByCategoryId(catId) > 0) {
            throw new ConflictException("Категория с id=" + catId + " связана с событиями и не может быть удалена");
        }
        categoryRepository.delete(category);
        log.info("Удалена категория {}", catId);
    }

    private Category getCategoryOrThrow(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));
    }

    private void checkNameIsFree(String name) {
        if (categoryRepository.existsByName(name)) {
            throw new ConflictException("Категория с названием " + name + " уже существует");
        }
    }
}
