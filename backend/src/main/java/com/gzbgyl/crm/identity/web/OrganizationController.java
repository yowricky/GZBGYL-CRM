package com.gzbgyl.crm.identity.web;

import com.gzbgyl.crm.identity.application.OrganizationNode;
import com.gzbgyl.crm.identity.application.OrganizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/organization-units")
public class OrganizationController {

    private final OrganizationService organizations;

    public OrganizationController(OrganizationService organizations) {
        this.organizations = organizations;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:admin')")
    public List<OrganizationTreeNode> listTree() {
        return toTree(organizations.findAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:admin')")
    public OrganizationNode create(@Valid @RequestBody CreateOrganizationRequest request) {
        if (request.parentId() == null) {
            return organizations.createRoot(request.code(), request.name());
        }
        return organizations.createChild(request.parentId(), request.code(), request.name());
    }

    @PatchMapping("/{id}/rename")
    @PreAuthorize("hasAuthority('system:admin')")
    public OrganizationNode rename(
            @PathVariable UUID id, @Valid @RequestBody RenameOrganizationRequest request) {
        return organizations.rename(id, request.name(), request.expectedVersion());
    }

    @PatchMapping("/{id}/move")
    @PreAuthorize("hasAuthority('system:admin')")
    public OrganizationNode move(
            @PathVariable UUID id, @Valid @RequestBody MoveOrganizationRequest request) {
        return organizations.move(id, request.newParentId(), request.expectedVersion());
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('system:admin')")
    public OrganizationNode deactivate(
            @PathVariable UUID id, @Valid @RequestBody ReasonedVersionRequest request) {
        return organizations.deactivate(id, request.expectedVersion());
    }

    private static List<OrganizationTreeNode> toTree(List<OrganizationNode> nodes) {
        LinkedHashMap<UUID, OrganizationTreeNode> byId = new LinkedHashMap<>();
        for (OrganizationNode node : nodes) {
            byId.put(node.id(), new OrganizationTreeNode(node.id(), node.parentId(), node.code(),
                    node.name(), node.path(), node.active(), node.version(), new ArrayList<>()));
        }

        List<OrganizationTreeNode> roots = new ArrayList<>();
        for (OrganizationTreeNode node : byId.values()) {
            OrganizationTreeNode parent = node.parentId() == null ? null : byId.get(node.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    public record OrganizationTreeNode(
            UUID id,
            UUID parentId,
            String code,
            String name,
            String path,
            boolean active,
            long version,
            List<OrganizationTreeNode> children) {
    }

    public record CreateOrganizationRequest(
            UUID parentId,
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 120) String name) {
    }

    public record RenameOrganizationRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @PositiveOrZero Long expectedVersion) {
    }

    public record MoveOrganizationRequest(
            @NotNull UUID newParentId,
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record ReasonedVersionRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 1000) String reason) {
    }
}
