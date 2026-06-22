package com.gzbgyl.crm.identity.persistence;

import com.gzbgyl.crm.identity.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByNormalizedUsername(String normalizedUsername);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select user from AppUser user where user.id = :id")
    Optional<AppUser> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<AppUser> findByNormalizedUsername(String normalizedUsername);
}
