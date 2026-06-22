package com.gzbgyl.crm.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzbgyl.crm.audit.domain.AuditLog;
import com.gzbgyl.crm.audit.persistence.AuditLogRepository;
import com.gzbgyl.crm.shared.api.InvalidRequestException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final Pattern NAME = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final List<String> SENSITIVE = List.of("password", "hash", "secret", "token", "auth",
            "authorization", "cookie", "session", "credential");
    private final AuditLogRepository repository;
    private final ObjectMapper mapper;

    public AuditService(AuditLogRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public AuditLog record(AuditCommand command) {
        validate(command);
        JsonNode before = command.before() == null ? null : mapper.valueToTree(redact(command.before()));
        JsonNode after = command.after() == null ? null : mapper.valueToTree(redact(command.after()));
        return repository.append(new AuditLog(command.actorId(), command.eventType(), command.aggregateType(),
                command.aggregateId(), before, after, command.ipAddress(), command.reason()));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> query(UUID actorId, String eventType, String aggregateType,
            UUID aggregateId, Instant from, Instant to, Pageable pageable) {
        return repository.query(actorId, eventType, aggregateType, aggregateId, from, to, pageable);
    }

    private static void validate(AuditCommand command) {
        if (command == null) throw new InvalidRequestException("Audit command is required");
        validName(command.eventType(), 100, "eventType");
        validName(command.aggregateType(), 150, "aggregateType");
        if (command.aggregateId() == null) throw new InvalidRequestException("aggregateId is required");
        if (command.reason() != null && command.reason().length() > 1000)
            throw new InvalidRequestException("reason must not exceed 1000 characters");
        if (command.ipAddress() != null && !validIp(command.ipAddress()))
            throw new InvalidRequestException("ipAddress must be a valid IP address");
    }

    private static void validName(String value, int max, String field) {
        if (value == null || value.length() > max || !NAME.matcher(value).matches())
            throw new InvalidRequestException(field + " must be an uppercase code of at most " + max + " characters");
    }

    private static boolean validIp(String value) {
        try {
            if (value.contains(":")) return InetAddress.getByName(value).getHostAddress().contains(":");
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) return false;
            for (String part : parts) {
                if (!part.matches("\\d{1,3}") || Integer.parseInt(part) > 255) return false;
            }
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static Object redact(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                String name = String.valueOf(key);
                copy.put(name, sensitive(name) ? "[REDACTED]" : redact(nested));
            });
            return copy;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            iterable.forEach(item -> copy.add(redact(item)));
            return copy;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int i = 0; i < length; i++) copy.add(redact(java.lang.reflect.Array.get(value, i)));
            return copy;
        }
        return value;
    }

    private static boolean sensitive(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE.stream().anyMatch(normalized::contains);
    }
}
