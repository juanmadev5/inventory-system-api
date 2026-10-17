package com.jm.inventory.infrastructure.user;

import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.user.User;
import com.jm.inventory.domain.user.UserRepository;
import com.jm.inventory.infrastructure.user.persistence.UserJpaEntity;
import com.jm.inventory.infrastructure.user.persistence.UserSpringDataRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserSpringDataRepository springDataRepository;

    public UserRepositoryAdapter(UserSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = springDataRepository.findById(user.getId())
                .map(existing -> applyTo(existing, user))
                .orElseGet(() -> toNewEntity(user));
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return springDataRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return springDataRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return springDataRepository.existsByEmail(email);
    }

    @Override
    public boolean existsAny() {
        return springDataRepository.count() > 0;
    }

    @Override
    public PagedResult<User> findAll(int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("email"));
        Page<UserJpaEntity> result = springDataRepository.findAllBy(pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    private UserJpaEntity applyTo(UserJpaEntity entity, User user) {
        entity.setEmail(user.getEmail());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setRole(user.getRole());
        entity.setActive(user.isActive());
        return entity;
    }

    private UserJpaEntity toNewEntity(User user) {
        return UserJpaEntity.builder()
                .id(user.getId())
                .email(user.getEmail())
                .passwordHash(user.getPasswordHash())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getId(), entity.getEmail(), entity.getPasswordHash(), entity.getRole(), entity.isActive(),
                entity.getCreatedAt());
    }
}
