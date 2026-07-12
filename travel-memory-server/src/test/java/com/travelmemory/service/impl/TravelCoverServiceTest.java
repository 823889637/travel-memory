package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelmemory.common.StoredFile;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.entity.TravelTrip;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.mapper.TravelTripMapper;
import com.travelmemory.service.FileStorageService;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.util.ImageMetadataExtractor;
import com.travelmemory.util.ImageMetadataInfo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class TravelCoverServiceTest {

    @Test
    void setsCoverFromPhotoBelongingToTrip() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTrip trip = trip(1L, null);
        TravelMemory memory = memory(10L, 1L, "/uploads/2026/07/photo.jpg");
        when(tripMapper.selectById(1L)).thenReturn(trip);
        when(memoryMapper.selectById(10L)).thenReturn(memory);

        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, memoryMapper);

        TravelTrip updated = service.setCover(1L, 10L);

        assertEquals("/uploads/2026/07/photo.jpg", updated.getCoverPhotoUrl());
        verify(tripMapper).updateById(trip);
    }

    @Test
    void rejectsCoverMemoryFromAnotherTrip() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        when(tripMapper.selectById(1L)).thenReturn(trip(1L, null));
        when(memoryMapper.selectById(10L)).thenReturn(memory(10L, 2L, "/uploads/other.jpg"));

        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, memoryMapper);

        assertThrows(BusinessException.class, () -> service.setCover(1L, 10L));
    }

    @Test
    void rejectsMemoryWithoutPhotoAsCover() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        when(tripMapper.selectById(1L)).thenReturn(trip(1L, null));
        when(memoryMapper.selectById(10L)).thenReturn(memory(10L, 1L, "  "));

        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, memoryMapper);

        assertThrows(BusinessException.class, () -> service.setCover(1L, 10L));
    }

    @Test
    void clearsExplicitCoverWithoutChangingMemoryPhotos() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelTrip trip = trip(1L, "/uploads/cover.jpg");
        when(tripMapper.selectById(1L)).thenReturn(trip);

        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, mock(TravelMemoryMapper.class));

        TravelTrip updated = service.clearCover(1L);

        assertNull(updated.getCoverPhotoUrl());
        verify(tripMapper).update(any(), any());
    }

    @Test
    void replacingCurrentCoverPhotoKeepsTheSameMemoryAsCover() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelTrip trip = trip(1L, "/uploads/old.jpg");
        when(tripMapper.selectById(1L)).thenReturn(trip);

        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, mock(TravelMemoryMapper.class));

        service.replaceCoverIfMatches(1L, " /uploads/old.jpg ", "/uploads/new.jpg");

        assertEquals("/uploads/new.jpg", trip.getCoverPhotoUrl());
        verify(tripMapper).update(any(), any());
    }

    @Test
    void deletingNonCoverMemoryDoesNotClearExplicitCover() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelTrip trip = trip(1L, "/uploads/cover.jpg");
        when(tripMapper.selectById(1L)).thenReturn(trip);

        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, mock(TravelMemoryMapper.class));

        service.clearCoverIfMatches(1L, "/uploads/other.jpg");

        assertEquals("/uploads/cover.jpg", trip.getCoverPhotoUrl());
        verify(tripMapper, never()).update(any(), any());
    }

    @Test
    void replacingNonCoverMemoryPhotoDoesNotChangeExplicitCover() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelTrip trip = trip(1L, "/uploads/cover.jpg");
        when(tripMapper.selectById(1L)).thenReturn(trip);

        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, mock(TravelMemoryMapper.class));

        service.replaceCoverIfMatches(1L, "/uploads/other.jpg", "/uploads/new.jpg");

        assertEquals("/uploads/cover.jpg", trip.getCoverPhotoUrl());
        verify(tripMapper, never()).update(any(), any());
    }

    @Test
    void regularTripUpdateCannotReplaceExplicitCover() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelTrip existing = trip(1L, "/uploads/cover.jpg");
        TravelTrip request = trip(99L, "https://example.test/not-allowed.jpg");
        when(tripMapper.selectById(1L)).thenReturn(existing);

        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, mock(TravelMemoryMapper.class));

        service.update(1L, request);

        assertEquals("/uploads/cover.jpg", request.getCoverPhotoUrl());
        assertEquals(1L, request.getId());
        verify(tripMapper).updateById(request);
    }

    @Test
    void listKeepsExplicitCoverSeparateFromEffectiveDefaultCover() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTrip trip = trip(1L, null);
        TravelMemory memory = memory(10L, 1L, "/uploads/first.jpg");
        when(tripMapper.selectList(any())).thenReturn(List.of(trip));
        when(memoryMapper.selectList(any())).thenReturn(List.of(memory));

        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, memoryMapper);

        var result = service.listForHome();

        assertNull(result.get(0).getCoverPhotoUrl());
        assertEquals("/uploads/first.jpg", result.get(0).getEffectiveCoverPhotoUrl());
        assertEquals(1L, result.get(0).getMemoryCount());
    }

    @Test
    void deletingCoverMemoryRequestsCoverClear() {
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTripService tripService = mock(TravelTripService.class);
        TravelMemory memory = memory(10L, 1L, "/uploads/cover.jpg");
        when(memoryMapper.selectById(10L)).thenReturn(memory);

        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(
                memoryMapper,
                tripService,
                mock(FileStorageService.class),
                mock(ImageMetadataExtractor.class));

        service.delete(10L);

        verify(memoryMapper).deleteById(10L);
        verify(tripService).clearCoverIfMatches(1L, "/uploads/cover.jpg");
    }

    @Test
    void replacingMemoryPhotoRequestsCoverSyncAfterMemoryUpdate() {
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTripService tripService = mock(TravelTripService.class);
        FileStorageService storageService = mock(FileStorageService.class);
        ImageMetadataExtractor metadataExtractor = mock(ImageMetadataExtractor.class);
        TravelMemory memory = memory(10L, 1L, "/uploads/old.jpg");
        when(memoryMapper.selectById(10L)).thenReturn(memory);
        when(storageService.store(any())).thenReturn(new StoredFile("/uploads/new.jpg", "/app/uploads/new.jpg"));
        when(metadataExtractor.extract("/app/uploads/new.jpg")).thenReturn(new ImageMetadataInfo());

        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(
                memoryMapper,
                tripService,
                storageService,
                metadataExtractor);

        service.uploadPhoto(10L, mock(MultipartFile.class));

        verify(memoryMapper).updateById(memory);
        verify(tripService).replaceCoverIfMatches(1L, "/uploads/old.jpg", "/uploads/new.jpg");
    }

    private TravelTrip trip(Long id, String coverPhotoUrl) {
        TravelTrip trip = new TravelTrip();
        trip.setId(id);
        trip.setTitle("Trip");
        trip.setCoverPhotoUrl(coverPhotoUrl);
        return trip;
    }

    private TravelMemory memory(Long id, Long tripId, String photoUrl) {
        TravelMemory memory = new TravelMemory();
        memory.setId(id);
        memory.setTripId(tripId);
        memory.setPhotoUrl(photoUrl);
        return memory;
    }
}
