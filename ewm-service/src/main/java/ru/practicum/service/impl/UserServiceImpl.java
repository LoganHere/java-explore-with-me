package ru.practicum.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.NewUserRequest;
import ru.practicum.dto.UserDto;
import ru.practicum.entity.User;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.UserMapper;
import ru.practicum.repository.UserRepository;
import ru.practicum.service.UserService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository repository;

    @Override
    public List<UserDto> getUsers(List<Long> ids, int from, int size) {
        Pageable pageable = PageRequest.of(from / Math.max(size, 1), Math.max(size, 1));
        List<User> users = ids == null || ids.isEmpty()
                ? repository.findAll(pageable).getContent()
                : repository.findByIdIn(ids, pageable).getContent();
        return users.stream().map(UserMapper::toDto).toList();
    }

    @Override
    @Transactional
    public UserDto createUser(NewUserRequest request) {
        if (repository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Пользователь с почтовым адресом " + request.getEmail() + " уже зарегистрирован");
        }
        User saved = repository.save(UserMapper.toEntity(request));
        log.info("Добавлен пользователь {} с почтой {}", saved.getId(), saved.getEmail());
        return UserMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        if (!repository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
        repository.deleteById(userId);
        log.info("Удалён пользователь {}", userId);
    }

    @Override
    public User getUserById(Long userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));
    }
}
