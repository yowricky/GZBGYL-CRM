package com.gzbgyl.crm.identity.web;

import com.gzbgyl.crm.identity.application.PermissionSummary;
import com.gzbgyl.crm.identity.application.RoleAdministrationService;
import com.gzbgyl.crm.identity.application.RoleSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class RoleAdministrationController {

    private final RoleAdministrationService roles;

    public RoleAdministrationController(RoleAdministrationService roles) {
        this.roles = roles;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('system:admin')")
    public List<RoleSummary> listRoles() {
        return roles.listRoles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('system:admin')")
    public List<PermissionSummary> listPermissions() {
        return roles.listPermissions();
    }

    @PatchMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('system:admin')")
    public RoleSummary replacePermissions(
            @PathVariable UUID id,
            @Valid @RequestBody ReplacePermissionsRequest request) {
        return roles.replacePermissions(id, request.permissionCodes(), request.expectedVersion(), request.reason());
    }

    public record ReplacePermissionsRequest(
            @NotEmpty Set<@NotBlank String> permissionCodes,
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 1000) String reason) {
    }
}
