package com.jm.inventory.api.user;

import com.jm.inventory.api.security.RequireAdmin;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.user.CreatedUser;
import com.jm.inventory.application.user.UserManagementService;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * {@code ADMIN}-only on every single method, including the reads - see
 * docs/specs/11-user-management/requirements.md's "Decision" for why even listing the team
 * roster isn't {@code STAFF}/{@code MANAGER}-visible.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
@RequireAdmin
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @Operation(summary = "List every user.")
    @ApiResponse(responseCode = "200", description = "Paginated list of users")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PagedResult<User> result = userManagementService.list(
                PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
        return PageResponse.of(
                result.items().stream().map(UserResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @Operation(summary = "Create a teammate with a one-time temporary password.")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "409", description = "USER_EMAIL_ALREADY_EXISTS")
    public ResponseEntity<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        CreatedUser created = userManagementService.create(request.email(), request.role());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.user().getId())
                .toUri();
        return ResponseEntity.created(location).body(CreatedUserResponse.from(created));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get a user by id.")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    public UserResponse getById(@PathVariable UUID userId) {
        return UserResponse.from(userManagementService.get(userId));
    }

    @PatchMapping("/{userId}/role")
    @Operation(summary = "Replace the user's role.")
    @ApiResponse(responseCode = "200", description = "Role changed")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    public UserResponse changeRole(@PathVariable UUID userId, @Valid @RequestBody ChangeUserRoleRequest request) {
        return UserResponse.from(userManagementService.changeRole(userId, request.role()));
    }

    @PostMapping("/{userId}/deactivation")
    @Operation(summary = "Disable the user's account.")
    @ApiResponse(responseCode = "200", description = "User deactivated")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    public UserResponse deactivate(@PathVariable UUID userId) {
        return UserResponse.from(userManagementService.deactivate(userId));
    }

    @PostMapping("/{userId}/reactivation")
    @Operation(summary = "Re-enable the user's account.")
    @ApiResponse(responseCode = "200", description = "User reactivated")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    public UserResponse reactivate(@PathVariable UUID userId) {
        return UserResponse.from(userManagementService.reactivate(userId));
    }
}
