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
import com.travelmemory.mapper.AppUserMapper;
import com.travelmemory.mapper.TripCompanionMapper;
import com.travelmemory.mapper.TripDraftMapper;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.mapper.MemoryPhotoMapper;
import com.travelmemory.mapper.MemoryDraftMapper;
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
        MemoryPhotoMapper photoMapper = mock(MemoryPhotoMapper.class);
        OrphanUploadCleanupServiceImpl service = service(memoryMapper, tripMapper, photoMapper, false, true);

        CleanupResult result = service.cleanupOrphans();

        assertEquals("Upload cleanup is disabled", result.getMessage());
        verify(memoryMapper, never()).selectObjs(any());
        verify(tripMapper, never()).selectObjs(any());
        verify(photoMapper, never()).selectObjs(any());
    }

    @Test
    void doesNotDeleteWhenDatabaseReferenceCollectionFails() throws IOException {
        Path orphan = oldFile("orphan.jpg");
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        MemoryPhotoMapper photoMapper = mock(MemoryPhotoMapper.class);
        when(memoryMapper.selectObjs(any())).thenThrow(new IllegalStateException("database unavailable"));
        OrphanUploadCleanupServiceImpl service = service(memoryMapper, tripMapper, photoMapper, true, false);

        CleanupResult result = service.cleanupOrphans();

        assertTrue(Files.exists(orphan));
        assertEquals(1, result.getFailedCount());
        assertEquals(0, result.getDeletedCount());
    }

    @Test
    void doesNotTreatAnExternalAbsoluteUrlAsALocalReference() throws IOException {
        Path orphan = oldFile("orphan.jpg");
        OrphanUploadCleanupService service = service(true, false,
                List.of("https://untrusted.example/uploads/orphan.jpg"), List.of());

        CleanupResult result = service.cleanupOrphans();

        assertFalse(Files.exists(orphan));
        assertEquals(0, result.getReferencedCount());
        assertEquals(1, result.getDeletedCount());
    }

    @Test
    void preservesOldTemporaryUploadFiles() throws IOException {
        Path temporaryFile = oldFile("in-progress.part");
        OrphanUploadCleanupService service = service(true, false, List.of(), List.of());

        CleanupResult result = service.cleanupOrphans();

        assertTrue(Files.exists(temporaryFile));
        assertEquals(0, result.getCandidateCount());
        assertEquals(0, result.getDeletedCount());
        assertTrue(result.getSkippedCount() >= 1);
    }

    @Test
    void preservesNonPrimaryMemoryPhotoReferences() throws IOException {
        Path primary = oldFile("primary.jpg");
        Path second = oldFile("second.jpg");
        Path orphan = oldFile("orphan.jpg");
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        MemoryPhotoMapper photoMapper = mock(MemoryPhotoMapper.class);
        when(memoryMapper.selectObjs(any())).thenReturn(List.of("/uploads/primary.jpg"));
        when(tripMapper.selectObjs(any())).thenReturn(List.of());
        when(photoMapper.selectObjs(any())).thenReturn(List.of("/uploads/primary.jpg", "/uploads/second.jpg"));
        OrphanUploadCleanupServiceImpl service = service(memoryMapper, tripMapper, photoMapper, true, false);

        CleanupResult result = service.cleanupOrphans();

        assertTrue(Files.exists(primary));
        assertTrue(Files.exists(second));
        assertFalse(Files.exists(orphan));
        assertEquals(2, result.getReferencedCount());
    }

    @Test
    void preservesPhotosReferencedOnlyByDrafts() throws IOException {
        Path draftPhoto = oldFile("draft.jpg");
        Path orphan = oldFile("orphan.jpg");
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        MemoryPhotoMapper photoMapper = mock(MemoryPhotoMapper.class);
        MemoryDraftMapper draftMapper = mock(MemoryDraftMapper.class);
        when(memoryMapper.selectObjs(any())).thenReturn(List.of());
        when(tripMapper.selectObjs(any())).thenReturn(List.of());
        when(photoMapper.selectObjs(any())).thenReturn(List.of());
        when(draftMapper.selectObjs(any())).thenReturn(List.of("/uploads/draft.jpg\n/uploads/another.jpg"));
        OrphanUploadCleanupServiceImpl service = service(
                memoryMapper, tripMapper, photoMapper, draftMapper, true, false);

        CleanupResult result = service.cleanupOrphans();

        assertTrue(Files.exists(draftPhoto));
        assertFalse(Files.exists(orphan));
        assertEquals(1, result.getReferencedCount());
    }

    @Test
    void preservesProfileCompanionAndTripDraftImages() throws IOException {
        Path profile = oldFile("profile.jpg");
        Path companion = oldFile("companion.jpg");
        Path tripDraft = oldFile("trip-draft.jpg");
        Path orphan = oldFile("orphan.jpg");
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        MemoryPhotoMapper photoMapper = mock(MemoryPhotoMapper.class);
        MemoryDraftMapper memoryDraftMapper = mock(MemoryDraftMapper.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        TripCompanionMapper companionMapper = mock(TripCompanionMapper.class);
        TripDraftMapper tripDraftMapper = mock(TripDraftMapper.class);
        when(memoryMapper.selectObjs(any())).thenReturn(List.of());
        when(tripMapper.selectObjs(any())).thenReturn(List.of());
        when(photoMapper.selectObjs(any())).thenReturn(List.of());
        when(memoryDraftMapper.selectObjs(any())).thenReturn(List.of());
        when(userMapper.selectObjs(any())).thenReturn(List.of("/uploads/profile.jpg"));
        when(companionMapper.selectObjs(any())).thenReturn(List.of("/uploads/companion.jpg"));
        when(tripDraftMapper.selectObjs(any())).thenReturn(List.of("/uploads/trip-draft.jpg"));
        UploadCleanupProperties properties = new UploadCleanupProperties();
        properties.setEnabled(true);
        properties.setDryRun(false);
        properties.setRetentionHours(24);
        OrphanUploadCleanupServiceImpl service = new OrphanUploadCleanupServiceImpl(
                memoryMapper, tripMapper, photoMapper, memoryDraftMapper,
                userMapper, companionMapper, tripDraftMapper, properties);
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        CleanupResult result = service.cleanupOrphans();

        assertTrue(Files.exists(profile));
        assertTrue(Files.exists(companion));
        assertTrue(Files.exists(tripDraft));
        assertFalse(Files.exists(orphan));
        assertEquals(3, result.getReferencedCount());
    }

    @Test
    void deletesRequestedUploadImmediatelyAfterItsFinalReferenceIsRemoved() throws IOException {
        Path removed = Files.writeString(tempDir.resolve("removed.jpg"), "removed");
        OrphanUploadCleanupService service = service(false, true, List.of(), List.of());

        service.deleteUnreferencedUploads(List.of("/uploads/removed.jpg"));

        assertFalse(Files.exists(removed));
    }

    @Test
    void preservesRequestedUploadWhenAnotherDatabaseReferenceStillExists() throws IOException {
        Path shared = Files.writeString(tempDir.resolve("shared.jpg"), "shared");
        OrphanUploadCleanupService service = service(false, true,
                List.of("/uploads/shared.jpg"), List.of());

        service.deleteUnreferencedUploads(List.of("/uploads/shared.jpg"));

        assertTrue(Files.exists(shared));
    }

    private OrphanUploadCleanupService service(
            boolean enabled,
            boolean dryRun,
            List<Object> memoryUrls,
            List<Object> tripCoverUrls
    ) {
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        MemoryPhotoMapper photoMapper = mock(MemoryPhotoMapper.class);
        when(memoryMapper.selectObjs(any())).thenReturn(memoryUrls);
        when(tripMapper.selectObjs(any())).thenReturn(tripCoverUrls);
        when(photoMapper.selectObjs(any())).thenReturn(List.of());
        return service(memoryMapper, tripMapper, photoMapper, enabled, dryRun);
    }

    private OrphanUploadCleanupServiceImpl service(
            TravelMemoryMapper memoryMapper,
            TravelTripMapper tripMapper,
            MemoryPhotoMapper photoMapper,
            boolean enabled,
            boolean dryRun
    ) {
        MemoryDraftMapper draftMapper = mock(MemoryDraftMapper.class);
        when(draftMapper.selectObjs(any())).thenReturn(List.of());
        return service(memoryMapper, tripMapper, photoMapper, draftMapper, enabled, dryRun);
    }

    private OrphanUploadCleanupServiceImpl service(
            TravelMemoryMapper memoryMapper,
            TravelTripMapper tripMapper,
            MemoryPhotoMapper photoMapper,
            MemoryDraftMapper draftMapper,
            boolean enabled,
            boolean dryRun
    ) {
        UploadCleanupProperties properties = new UploadCleanupProperties();
        properties.setEnabled(enabled);
        properties.setDryRun(dryRun);
        properties.setRetentionHours(24);
        AppUserMapper appUserMapper = mock(AppUserMapper.class);
        TripCompanionMapper companionMapper = mock(TripCompanionMapper.class);
        TripDraftMapper tripDraftMapper = mock(TripDraftMapper.class);
        when(appUserMapper.selectObjs(any())).thenReturn(List.of());
        when(companionMapper.selectObjs(any())).thenReturn(List.of());
        when(tripDraftMapper.selectObjs(any())).thenReturn(List.of());
        OrphanUploadCleanupServiceImpl service = new OrphanUploadCleanupServiceImpl(
                memoryMapper, tripMapper, photoMapper, draftMapper,
                appUserMapper, companionMapper, tripDraftMapper, properties);
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        return service;
    }

    private Path oldFile(String filename) throws IOException {
        Path file = Files.writeString(tempDir.resolve(filename), filename);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(Duration.ofHours(25))));
        return file;
    }
}
