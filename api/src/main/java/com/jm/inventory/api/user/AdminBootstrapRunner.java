package com.jm.inventory.api.user;

import com.jm.inventory.api.security.BootstrapAdminProperties;
import com.jm.inventory.application.user.PasswordHasher;
import com.jm.inventory.domain.user.Role;
import com.jm.inventory.domain.user.User;
import com.jm.inventory.domain.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the very first {@code ADMIN} - with no external identity provider, this
 * application needs some way to bootstrap into existence with zero users at all. Runs in
 * every profile (not {@code @Profile}-gated): a blank {@code APP_ADMIN_EMAIL}/
 * {@code APP_ADMIN_PASSWORD} simply skips bootstrapping (logged, never a startup failure),
 * and an already-populated {@code users} table skips it too, so this is always safe to
 * leave wired in.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final BootstrapAdminProperties bootstrapAdminProperties;

    public AdminBootstrapRunner(
            UserRepository userRepository, PasswordHasher passwordHasher,
            BootstrapAdminProperties bootstrapAdminProperties) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.bootstrapAdminProperties = bootstrapAdminProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsAny()) {
            return;
        }
        String email = bootstrapAdminProperties.email();
        String password = bootstrapAdminProperties.password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            LOG.warn("APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD not set and no user exists yet - "
                    + "no bootstrap ADMIN was created.");
            return;
        }
        userRepository.save(new User(UUID.randomUUID(), email, passwordHasher.hash(password), Role.ADMIN, true, Instant.now()));
        LOG.info("Bootstrapped the first ADMIN user ({}).", email);
    }
}
