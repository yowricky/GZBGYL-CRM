package com.gzbgyl.crm.attachment.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AttachmentCleanupJob {
    private final AttachmentService service;
    private final int batchSize;

    public AttachmentCleanupJob(AttachmentService service,
            @Value("${app.attachments.cleanup-batch-size:50}") int batchSize) {
        this.service = service;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.attachments.cleanup-delay-ms:60000}")
    void run() {
        service.cleanupPendingDeletes(batchSize);
    }
}
