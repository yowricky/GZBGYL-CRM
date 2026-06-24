package com.gzbgyl.crm.contract.persistence;

import com.gzbgyl.crm.contract.domain.Contract;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    Optional<Contract> findByContractNumber(String contractNumber);

    List<Contract> findByAccountId(UUID accountId);

    List<Contract> findByStatus(String status);
}
