package com.jm.inventory.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.jm.inventory.application.user.AccessTokenIssuer.IssuedAccessToken;
import com.jm.inventory.domain.user.InvalidCredentialsException;
import com.jm.inventory.domain.user.Role;
import com.jm.inventory.domain.user.User;
import com.jm.inventory.domain.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A pure unit test - every collaborator mocked, per docs/conventions/testing.md. */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    private AuthenticationService authenticationService;

    @Test
    void given_aCorrectPasswordForAnActiveUser_when_loggingIn_then_issuesAnAccessToken() {
        // Given
        authenticationService = new AuthenticationService(userRepository, passwordHasher, accessTokenIssuer);
        User user = anActiveUser();
        IssuedAccessToken issuedAccessToken = new IssuedAccessToken("token-value", 3600);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", user.getPasswordHash())).thenReturn(true);
        when(accessTokenIssuer.issue(user)).thenReturn(issuedAccessToken);

        // When
        IssuedAccessToken result = authenticationService.login(user.getEmail(), "correct-password");

        // Then
        assertThat(result).isEqualTo(issuedAccessToken);
    }

    @Test
    void given_anUnknownEmail_when_loggingIn_then_invalidCredentials() {
        // Given
        authenticationService = new AuthenticationService(userRepository, passwordHasher, accessTokenIssuer);
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authenticationService.login("nobody@example.com", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void given_aWrongPassword_when_loggingIn_then_invalidCredentials() {
        // Given
        authenticationService = new AuthenticationService(userRepository, passwordHasher, accessTokenIssuer);
        User user = anActiveUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> authenticationService.login(user.getEmail(), "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void given_aDeactivatedUser_when_loggingInWithTheCorrectPassword_then_invalidCredentials() {
        // Given
        authenticationService = new AuthenticationService(userRepository, passwordHasher, accessTokenIssuer);
        User inactiveUser = new User(
                UUID.randomUUID(), "inactive@example.com", "hashed-password", Role.STAFF, false, Instant.now());
        when(userRepository.findByEmail(inactiveUser.getEmail())).thenReturn(Optional.of(inactiveUser));

        // When / Then
        assertThatThrownBy(() -> authenticationService.login(inactiveUser.getEmail(), "correct-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private User anActiveUser() {
        return new User(UUID.randomUUID(), "staff@example.com", "hashed-password", Role.STAFF, true, Instant.now());
    }
}
