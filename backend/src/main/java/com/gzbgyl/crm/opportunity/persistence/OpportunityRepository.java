package com.gzbgyl.crm.opportunity.persistence;

import com.gzbgyl.crm.opportunity.domain.Opportunity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {

    List<Opportunity> findByAccountId(UUID accountId);

    List<Opportunity> findByOwnerId(UUID ownerId);

    List<Opportunity> findByStage(String stage);
}
