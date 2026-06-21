package com.gzbgyl.crm.identity.persistence;

import com.gzbgyl.crm.identity.domain.AppUser;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByNormalizedUsername(String normalizedUsername);
}
