package com.gzbgyl.crm.identity.persistence;

import com.gzbgyl.crm.identity.domain.Permission;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findAllByOrderByCodeAsc();

    Set<Permission> findAllByCodeIn(Collection<String> codes);
}
