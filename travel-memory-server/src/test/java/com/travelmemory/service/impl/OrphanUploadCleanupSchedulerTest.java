package com.travelmemory.service.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.travelmemory.config.UploadCleanupProperties;
import com.travelmemory.service.OrphanUploadCleanupService;
import org.junit.jupiter.api.Test;

class OrphanUploadCleanupSchedulerTest {

    @Test
    void doesNotInvokeCleanupWhenDisabled() {
        OrphanUploadCleanupService cleanupService = mock(OrphanUploadCleanupService.class);
        UploadCleanupProperties properties = new UploadCleanupProperties();
        properties.setEnabled(false);
        OrphanUploadCleanupScheduler scheduler = new OrphanUploadCleanupScheduler(cleanupService, properties);

        scheduler.cleanupOrphans();

        verify(cleanupService, never()).cleanupOrphans();
    }
}
