package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travelmemory.entity.TravelMemory;
import com.travelmemory.entity.TravelTrip;
import com.travelmemory.service.TravelMemoryService;
import com.travelmemory.service.TravelTripService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TripRecapServiceImplTest {

    @Test
    void aggregatesRealMemoriesByCalendarDay() {
        TravelTripService trips = mock(TravelTripService.class);
        TravelMemoryService memories = mock(TravelMemoryService.class);
        TravelTrip trip = new TravelTrip();
        trip.setId(3L);
        trip.setStartDate(LocalDate.of(2026, 7, 3));
        trip.setEndDate(LocalDate.of(2026, 7, 6));
        TravelMemory first = memory(10L, "海河", LocalDateTime.of(2026, 7, 3, 8, 15), 2, true, true);
        TravelMemory second = memory(11L, "海河", LocalDateTime.of(2026, 7, 3, 21, 5), 1, false, false);
        TravelMemory third = memory(12L, "狮子林桥", LocalDateTime.of(2026, 7, 5, 10, 30), 3, true, true);
        when(trips.getById(3L)).thenReturn(trip);
        when(memories.timeline(3L)).thenReturn(List.of(first, second, third));

        var result = new TripRecapServiceImpl(trips, memories).get(3L);

        assertEquals(4, result.tripDays());
        assertEquals(3, result.memoryCount());
        assertEquals(6, result.photoCount());
        assertEquals(2, result.favoriteCount());
        assertEquals(2, result.locatedMemoryCount());
        assertEquals(2, result.locationCount());
        assertEquals(2, result.days().size());
        assertEquals(1, result.days().get(0).dayNumber());
        assertEquals(3, result.days().get(1).dayNumber());
        assertEquals(2, result.days().get(0).memoryCount());
        assertEquals("海河", result.topLocations().get(0).name());
    }

    private TravelMemory memory(Long id, String location, LocalDateTime time, int photoCount,
            boolean favorite, boolean located) {
        TravelMemory memory = new TravelMemory();
        memory.setId(id);
        memory.setTripId(3L);
        memory.setLocationName(location);
        memory.setRecordTime(time);
        memory.setPhotoUrl("/uploads/users/1/" + id + ".jpg");
        memory.setPhotoCount(photoCount);
        memory.setIsFavorite(favorite ? 1 : 0);
        if (located) {
            memory.setLatitude(BigDecimal.valueOf(39.1));
            memory.setLongitude(BigDecimal.valueOf(117.2));
        }
        return memory;
    }
}
