package com.gzbgyl.crm.identity.web;

import com.gzbgyl.crm.identity.application.CreateUserCommand;
import com.gzbgyl.crm.identity.application.UserAdministrationService;
import com.gzbgyl.crm.identity.application.UserSearchQuery;
import com.gzbgyl.crm.identity.application.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdministrationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserAdministrationService users;

    public UserAdministrationController(UserAdministrationService users) {
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:admin')")
    public Page<UserSummary> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID organizationUnitId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new com.gzbgyl.crm.shared.api.InvalidRequestException(
                    "page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return users.searchUsers(new UserSearchQuery(keyword, organizationUnitId, active),
                PageRequest.of(page, size, Sort.by("username").ascending()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:admin')")
    public UserSummary create(@Valid @RequestBody CreateUserRequest request) {
        return users.createUser(new CreateUserCommand(request.username(), request.displayName(),
                request.initialPassword(), request.organizationUnitId(), request.roleCodes()));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('system:admin')")
    public UserSummary activate(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return users.activate(id, request.expectedVersion());
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('system:admin')")
    public UserSummary deactivate(
            @PathVariable UUID id, @Valid @RequestBody ReasonedVersionRequest request) {
        return users.deactivate(id, request.expectedVersion());
    }

    @PatchMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:admin')")
    public UserSummary resetPassword(
            @PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        return users.resetPassword(id, request.password(), request.expectedVersion());
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:admin')")
    public UserSummary assignRoles(@PathVariable UUID id, @Valid @RequestBody AssignRolesRequest request) {
        return users.assignRoles(id, request.roleCodes(), request.expectedVersion());
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 80) String username,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = 12, max = 72) String initialPassword,
            @NotNull UUID organizationUnitId,
            @NotEmpty Set<@NotBlank String> roleCodes) {
    }

    public record VersionRequest(@NotNull @PositiveOrZero Long expectedVersion) {
    }

    public record ReasonedVersionRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotNull @PositiveOrZero Long expectedVersion) {
    }

    public record AssignRolesRequest(
            @NotEmpty Set<@NotBlank String> roleCodes,
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 1000) String reason) {
    }
}
