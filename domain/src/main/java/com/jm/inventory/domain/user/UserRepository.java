package com.jm.inventory.domain.user;

import com.jm.inventory.domain.common.PagedResult;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** @return whether at least one user exists - used only to decide if bootstrap should run. */
    boolean existsAny();

    PagedResult<User> findAll(int page, int pageSize);
}
