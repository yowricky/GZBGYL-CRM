package com.gzbgyl.crm.identity.persistence;

import com.gzbgyl.crm.identity.domain.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    @EntityGraph(attributePaths = "permissions")
    Set<Role> findAllByCodeIn(Collection<String> codes);

    @EntityGraph(attributePaths = "permissions")
    @Query("select role from Role role order by role.code")
    List<Role> findAllDetailedOrderByCode();

    @EntityGraph(attributePaths = "permissions")
    @Query("select role from Role role where role.id = :id")
    Optional<Role> findDetailedById(@Param("id") UUID id);
}
