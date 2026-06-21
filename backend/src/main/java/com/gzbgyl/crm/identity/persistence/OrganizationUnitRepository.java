package com.gzbgyl.crm.identity.persistence;

import com.gzbgyl.crm.identity.domain.OrganizationUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, UUID> {

    boolean existsByCode(String code);

    List<OrganizationUnit> findByPathStartingWithOrderByPathAsc(String pathPrefix);

    @Query(value = "select pg_advisory_xact_lock(684729104531)", nativeQuery = true)
    void acquireHierarchyMutationLock();

    @Query(value = """
            select count(*) > 0
            from organization_unit ancestor
            where ancestor.active = false
              and cast(:path as varchar) like ancestor.path || '%'
            """, nativeQuery = true)
    boolean hasInactiveAncestorOrSelf(@Param("path") String path);

    @Query(value = """
            select count(*) > 0
            from organization_unit descendant
            where descendant.active = true
              and descendant.id <> :id
              and descendant.path like cast(:pathPrefix as varchar) || '%'
            """, nativeQuery = true)
    boolean hasActiveDescendant(
            @Param("id") UUID id, @Param("pathPrefix") String pathPrefix);

    @Query(value = """
            select max(char_length(path))
            from organization_unit
            where path like cast(:pathPrefix as varchar) || '%'
            """, nativeQuery = true)
    int maximumSubtreePathLength(@Param("pathPrefix") String pathPrefix);

    @Modifying
    @Query(value = """
            update organization_unit
            set path = cast(:newPrefix as varchar)
                    || substring(path from char_length(cast(:oldPrefix as varchar)) + 1),
                version = version + 1,
                updated_at = current_timestamp
            where id <> :rootId
              and path like cast(:oldPrefix as varchar) || '%'
            """, nativeQuery = true)
    int replaceDescendantPathPrefix(
            @Param("rootId") UUID rootId,
            @Param("oldPrefix") String oldPrefix,
            @Param("newPrefix") String newPrefix);
}
