package com.gzbgyl.crm.contract.persistence;

import com.gzbgyl.crm.contract.domain.Payment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByContractId(UUID contractId);

    List<Payment> findByStatus(String status);
}
