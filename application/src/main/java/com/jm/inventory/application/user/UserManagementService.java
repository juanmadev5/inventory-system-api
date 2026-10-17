package com.jm.inventory.application.user;

import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.user.Role;
import com.jm.inventory.domain.user.User;
import com.jm.inventory.domain.user.UserEmailAlreadyExistsException;
import com.jm.inventory.domain.user.UserNotFoundException;
import com.jm.inventory.domain.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** See docs/specs/11-user-management/requirements.md. */
@Service
@Transactional
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final AuditLogService auditLogService;

    public UserManagementService(
            UserRepository userRepository, PasswordHasher passwordHasher,
            TemporaryPasswordGenerator temporaryPasswordGenerator, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.auditLogService = auditLogService;
    }

    public CreatedUser create(String email, Role role) {
        if (userRepository.existsByEmail(email)) {
            throw new UserEmailAlreadyExistsException(email);
        }
        String temporaryPassword = temporaryPasswordGenerator.generate();
        User created = userRepository.save(new User(
                UUID.randomUUID(), email, passwordHasher.hash(temporaryPassword), role, true, Instant.now()));
        auditLogService.recordCreated(AuditEntityType.USER, created.getId(), UserSnapshot.from(created));
        return new CreatedUser(created, temporaryPassword);
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional(readOnly = true)
    public PagedResult<User> list(int page, int pageSize) {
        return userRepository.findAll(page, pageSize);
    }

    public User changeRole(UUID userId, Role role) {
        User existing = get(userId);
        User updated = userRepository.save(new User(
                existing.getId(), existing.getEmail(), existing.getPasswordHash(), role, existing.isActive(),
                existing.getCreatedAt()));
        auditLogService.recordUpdated(
                AuditEntityType.USER, userId, UserSnapshot.from(existing), UserSnapshot.from(updated));
        return updated;
    }

    public User deactivate(UUID userId) {
        return setActive(userId, false);
    }

    public User reactivate(UUID userId) {
        return setActive(userId, true);
    }

    private User setActive(UUID userId, boolean active) {
        User existing = get(userId);
        User updated = new User(
                existing.getId(), existing.getEmail(), existing.getPasswordHash(), existing.getRole(), active,
                existing.getCreatedAt());
        User saved = userRepository.save(updated);
        auditLogService.recordUpdated(AuditEntityType.USER, userId, UserSnapshot.from(existing), UserSnapshot.from(saved));
        return saved;
    }
}
