package com.gzbgyl.crm.audit.persistence;

import com.gzbgyl.crm.audit.domain.AuditLog;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {
    private final EntityManager entityManager;

    public AuditLogRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public AuditLog append(AuditLog log) {
        entityManager.persist(log);
        entityManager.flush();
        return log;
    }

    public Optional<AuditLog> findById(UUID id) {
        return Optional.ofNullable(entityManager.find(AuditLog.class, id));
    }

    public long count() {
        return entityManager.createQuery("select count(a) from AuditLog a", Long.class).getSingleResult();
    }

    public Page<AuditLog> query(UUID actorId, String eventType, String aggregateType,
            UUID aggregateId, Instant from, Instant to, Pageable pageable) {
        StringBuilder where = new StringBuilder(" where 1=1");
        Map<String, Object> values = new HashMap<>();
        add(where, values, "actorId", "a.actorId = :actorId", actorId);
        add(where, values, "eventType", "a.eventType = :eventType", eventType);
        add(where, values, "aggregateType", "a.aggregateType = :aggregateType", aggregateType);
        add(where, values, "aggregateId", "a.aggregateId = :aggregateId", aggregateId);
        add(where, values, "from", "a.createdAt >= :from", from);
        add(where, values, "to", "a.createdAt <= :to", to);
        var contentQuery = entityManager.createQuery(
                "select a from AuditLog a" + where + " order by a.createdAt desc, a.id desc", AuditLog.class);
        var countQuery = entityManager.createQuery("select count(a) from AuditLog a" + where, Long.class);
        values.forEach((name, value) -> { contentQuery.setParameter(name, value); countQuery.setParameter(name, value); });
        contentQuery.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        return new PageImpl<>(contentQuery.getResultList(), pageable, countQuery.getSingleResult());
    }

    private static void add(StringBuilder where, Map<String, Object> values,
            String name, String clause, Object value) {
        if (value != null) { where.append(" and ").append(clause); values.put(name, value); }
    }
}
