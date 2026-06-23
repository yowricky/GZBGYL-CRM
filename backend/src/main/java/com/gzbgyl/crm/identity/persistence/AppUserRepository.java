package com.gzbgyl.crm.identity.persistence;

import com.gzbgyl.crm.identity.domain.AppUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select user.id from AppUser user
            where (:organizationUnitId is null or user.organizationUnitId = :organizationUnitId)
              and (:active is null or user.active = :active)
              and (:keyword is null
                   or lower(user.username) like lower(concat('%', :keyword, '%'))
                   or lower(user.displayName) like lower(concat('%', :keyword, '%')))
            """)
    Page<UUID> searchIds(
            @Param("keyword") String keyword,
            @Param("organizationUnitId") UUID organizationUnitId,
            @Param("active") Boolean active,
            Pageable pageable);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    List<AppUser> findAllDetailedByIdIn(Collection<UUID> ids);
}
