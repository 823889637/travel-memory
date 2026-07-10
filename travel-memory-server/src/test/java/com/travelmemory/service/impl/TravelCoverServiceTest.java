package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
