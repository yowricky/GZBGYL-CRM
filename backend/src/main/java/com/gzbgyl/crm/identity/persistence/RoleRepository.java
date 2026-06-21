package com.gzbgyl.crm.identity.persistence;

import com.gzbgyl.crm.identity.domain.Role;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    @EntityGraph(attributePaths = "permissions")
    Set<Role> findAllByCodeIn(Collection<String> codes);
}
