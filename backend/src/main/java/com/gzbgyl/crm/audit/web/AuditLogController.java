package com.gzbgyl.crm.audit.web;

import com.gzbgyl.crm.audit.application.AuditService;
import com.gzbgyl.crm.shared.api.InvalidRequestException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {
    private static final int MAX_PAGE_SIZE = 100;
    private final AuditService audit;

    public AuditLogController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:admin')")
    public AuditLogPageResponse query(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) UUID aggregateId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new InvalidRequestException("page must be at least 0");
        if (size < 1 || size > MAX_PAGE_SIZE)
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        if ((long) page * size > Integer.MAX_VALUE)
            throw new InvalidRequestException("page offset is too large");
        if (from != null && to != null && from.isAfter(to))
            throw new InvalidRequestException("from must not be after to");
        var result = audit.query(actorId, eventType, aggregateType, aggregateId, from, to,
                PageRequest.of(page, size)).map(AuditLogResponse::from);
        return AuditLogPageResponse.from(result);
    }
}
