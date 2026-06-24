package com.gzbgyl.crm.lead.persistence;

import com.gzbgyl.crm.lead.domain.Lead;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
}
