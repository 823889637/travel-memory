package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelmemory.entity.TravelMemory;
import com.travelmemory.entity.TravelTrip;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.MemoryPhotoMapper;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.mapper.TravelTripMapper;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.service.FileStorageService;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.util.ImageMetadataExtractor;
import org.junit.jupiter.api.Test;

class UserOwnershipServiceTest {

    @Test
    void userCannotReadUpdateOrDeleteAnotherUsersTrip() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelTrip otherUsersTrip = trip(20L, 2L);
        when(tripMapper.selectById(20L)).thenReturn(otherUsersTrip);
        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, mock(TravelMemoryMapper.class),
                mock(MemoryPhotoMapper.class), currentUser(1L));

        assertNotFound(() -> service.getById(20L));
        assertNotFound(() -> service.update(20L, trip(null, null)));
        assertNotFound(() -> service.delete(20L));
        verify(tripMapper, never()).updateById(any(TravelTrip.class));
        verify(tripMapper, never()).deleteById(20L);
    }

    @Test
    void userCannotReadOrChangeAnotherUsersMemory() {
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        TravelTripService trips = mock(TravelTripService.class);
        TravelMemory otherUsersMemory = memory(30L, 20L);
        when(memoryMapper.selectById(30L)).thenReturn(otherUsersMemory);
        when(trips.getById(20L)).thenThrow(new BusinessException(404, "Trip not found"));
        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(memoryMapper, mock(MemoryPhotoMapper.class), trips,
                mock(FileStorageService.class), mock(ImageMetadataExtractor.class), currentUser(1L));

        assertNotFound(() -> service.getById(30L));
        assertNotFound(() -> service.favorite(30L, true));
        assertNotFound(() -> service.delete(30L));
        verify(memoryMapper, never()).updateById(any(TravelMemory.class));
        verify(memoryMapper, never()).deleteById(30L);
    }

    @Test
    void userCannotSetAnotherUsersMemoryAsCover() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        when(tripMapper.selectById(10L)).thenReturn(trip(10L, 1L));
        when(tripMapper.selectById(20L)).thenReturn(trip(20L, 2L));
        when(memoryMapper.selectById(30L)).thenReturn(memory(30L, 20L));
        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, memoryMapper,
                mock(MemoryPhotoMapper.class), currentUser(1L));

        assertNotFound(() -> service.setCover(10L, 30L));
        verify(tripMapper, never()).updateById(any(TravelTrip.class));
    }

    private void assertNotFound(Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(404, exception.getCode());
    }

    private CurrentUser currentUser(Long id) {
        CurrentUser current = mock(CurrentUser.class);
        when(current.requireId()).thenReturn(id);
        return current;
    }

    private TravelTrip trip(Long id, Long userId) {
        TravelTrip trip = new TravelTrip();
        trip.setId(id);
        trip.setUserId(userId);
        trip.setTitle("Trip");
        return trip;
    }

    private TravelMemory memory(Long id, Long tripId) {
        TravelMemory memory = new TravelMemory();
        memory.setId(id);
        memory.setTripId(tripId);
        memory.setPhotoUrl("/uploads/users/2/2026/07/photo.jpg");
        return memory;
    }
}
