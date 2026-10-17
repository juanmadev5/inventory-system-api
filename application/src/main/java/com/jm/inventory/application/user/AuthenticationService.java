package com.jm.inventory.application.user;

import com.jm.inventory.application.user.AccessTokenIssuer.IssuedAccessToken;
import com.jm.inventory.domain.user.InvalidCredentialsException;
import com.jm.inventory.domain.user.User;
import com.jm.inventory.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberately never reveals, via timing or response shape, whether a given email exists -
 * an unknown email, a wrong password, and an inactive account all fail identically with
 * {@link InvalidCredentialsException}.
 */
@Service
@Transactional(readOnly = true)
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;

    public AuthenticationService(
            UserRepository userRepository, PasswordHasher passwordHasher, AccessTokenIssuer accessTokenIssuer) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
    }

    public IssuedAccessToken login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive() || !passwordHasher.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return accessTokenIssuer.issue(user);
    }
}
