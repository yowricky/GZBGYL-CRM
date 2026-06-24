package com.gzbgyl.crm.account.persistence;

import com.gzbgyl.crm.account.domain.Account;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    java.util.List<Account> findByOwnerId(UUID ownerId);
}
