package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelmemory.config.UploadCleanupProperties;
import com.travelmemory.dto.CleanupResult;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.mapper.TravelTripMapper;
import com.travelmemory.service.OrphanUploadCleanupService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class OrphanUploadCleanupServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void preservesMemoryAndTripCoverReferences() throws IOException {
        Path memoryPhoto = oldFile("memory.jpg");
        Path coverPhoto = oldFile("cover.jpg");
        Path orphanPhoto = oldFile("orphan.jpg");
        OrphanUploadCleanupService service = service(true, false,
                List.of("/uploads/memory.jpg"), List.of("uploads/cover.jpg"));

        CleanupResult result = service.cleanupOrphans();

        assertTrue(Files.exists(memoryPhoto));
        assertTrue(Files.exists(coverPhoto));
        assertFalse(Files.exists(orphanPhoto));
        assertEquals(2, result.getReferencedCount());
        assertEquals(1, result.getDeletedCount());
    }

    @Test
    void preservesRecentOrphanAndDeletesOnlyInDryRunFalse() throws IOException {
        Path oldOrphan = oldFile("old.jpg");
        Path recentOrphan = Files.writeString(tempDir.resolve("recent.jpg"), "recent");
        OrphanUploadCleanupService service = service(true, true, List.of(), List.of());

        CleanupResult dryRun = service.cleanupOrphans();

        assertTrue(Files.exists(oldOrphan));
        assertTrue(Files.exists(recentOrphan));
        assertEquals(1, dryRun.getCandidateCount());
        assertEquals(0, dryRun.getDeletedCount());
        assertEquals(1, dryRun.getRecentCount());
    }

    @Test
    void skipsCleanupWhenDisabledWithoutQueryingDatabase() throws IOException {
        oldFile("orphan.jpg");
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        OrphanUploadCleanupServiceImpl service = service(memoryMapper, tripMapper, false, true);

        CleanupResult result = service.cleanupOrphans();

        assertEquals("Upload cleanup is disabled", result.getMessage());
        verify(memoryMapper, never()).selectObjs(any());
        verify(tripMapper, never()).selectObjs(any());
    }

    @Test
    void doesNotDeleteWhenDatabaseReferenceCollectionFails() throws IOException {
        Path orphan = oldFile("orphan.jpg");
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        when(memoryMapper.selectObjs(any())).thenThrow(new IllegalStateException("database unavailable"));
        OrphanUploadCleanupServiceImpl service = service(memoryMapper, tripMapper, true, false);

        CleanupResult result = service.cleanupOrphans();

        assertTrue(Files.exists(orphan));
        assertEquals(1, result.getFailedCount());
        assertEquals(0, result.getDeletedCount());
    }

    private OrphanUploadCleanupService service(
            boolean enabled,
            boolean dryRun,
            List<Object> memoryUrls,
            List<Object> tripCoverUrls
    ) {
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        when(memoryMapper.selectObjs(any())).thenReturn(memoryUrls);
        when(tripMapper.selectObjs(any())).thenReturn(tripCoverUrls);
        return service(memoryMapper, tripMapper, enabled, dryRun);
    }

    private OrphanUploadCleanupServiceImpl service(
            TravelMemoryMapper memoryMapper,
            TravelTripMapper tripMapper,
            boolean enabled,
            boolean dryRun
    ) {
        UploadCleanupProperties properties = new UploadCleanupProperties();
        properties.setEnabled(enabled);
        properties.setDryRun(dryRun);
        properties.setRetentionHours(24);
        OrphanUploadCleanupServiceImpl service = new OrphanUploadCleanupServiceImpl(
                memoryMapper,
                tripMapper,
                properties);
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        return service;
    }

    private Path oldFile(String filename) throws IOException {
        Path file = Files.writeString(tempDir.resolve(filename), filename);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(Duration.ofHours(25))));
        return file;
    }
}
