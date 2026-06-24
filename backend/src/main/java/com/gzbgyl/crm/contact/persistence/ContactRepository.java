package com.gzbgyl.crm.contact.persistence;

import com.gzbgyl.crm.contact.domain.Contact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    List<Contact> findByAccountId(UUID accountId);

}
