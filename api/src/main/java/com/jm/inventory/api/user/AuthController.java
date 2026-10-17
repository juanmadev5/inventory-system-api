package com.jm.inventory.api.user;

import com.jm.inventory.application.user.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The one public business endpoint - see {@code SecurityConfig}'s {@code PUBLIC_PATHS}. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange an email/password for a bearer access token.")
    @ApiResponse(responseCode = "200", description = "Login succeeded")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED - invalid email or password")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(authenticationService.login(request.email(), request.password()));
    }
}
