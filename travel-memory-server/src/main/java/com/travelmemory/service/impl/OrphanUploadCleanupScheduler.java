package com.travelmemory.service.impl;

import com.travelmemory.config.UploadCleanupProperties;
import com.travelmemory.service.OrphanUploadCleanupService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrphanUploadCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrphanUploadCleanupScheduler.class);

    private final OrphanUploadCleanupService cleanupService;
    private final UploadCleanupProperties cleanupProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OrphanUploadCleanupScheduler(
            OrphanUploadCleanupService cleanupService,
            UploadCleanupProperties cleanupProperties
    ) {
        this.cleanupService = cleanupService;
        this.cleanupProperties = cleanupProperties;
    }

    @Scheduled(cron = "${app.upload.cleanup.cron:0 30 3 * * ?}")
    public void cleanupOrphans() {
        if (!cleanupProperties.isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("Upload cleanup skipped because a previous run is still active");
            return;
        }
        try {
            cleanupService.cleanupOrphans();
        } finally {
            running.set(false);
        }
    }
}
