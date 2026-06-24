package com.gzbgyl.crm.quote.persistence;

import com.gzbgyl.crm.quote.domain.Quote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    List<Quote> findByOpportunityId(UUID opportunityId);

    List<Quote> findByStage(String stage);
}
