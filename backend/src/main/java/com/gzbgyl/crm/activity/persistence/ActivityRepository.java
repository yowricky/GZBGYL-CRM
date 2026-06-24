package com.gzbgyl.crm.activity.persistence;

import com.gzbgyl.crm.activity.domain.Activity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    List<Activity> findByAssignedToId(UUID assignedToId);

    List<Activity> findByRefEntityTypeAndRefEntityId(String refEntityType, String refEntityId);
}
