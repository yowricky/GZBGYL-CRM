package com.gzbgyl.crm.identity.persistence;

import com.gzbgyl.crm.identity.domain.OrganizationUnit;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, UUID> {

    boolean existsByCode(String code);

    List<OrganizationUnit> findByPathStartingWithOrderByPathAsc(String pathPrefix);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("select unit from OrganizationUnit unit where unit.id = :id")
    Optional<OrganizationUnit> findByIdForUpdate(UUID id);
}
