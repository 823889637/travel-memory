package com.travelmemory.dto;

import com.travelmemory.entity.TravelMemory;
import com.travelmemory.entity.TravelTrip;
import java.util.List;

public record TripRecapResponse(
        TravelTrip trip,
        long tripDays,
        long memoryCount,
        long photoCount,
        long favoriteCount,
        long locatedMemoryCount,
        long locationCount,
        List<TripRecapDay> days,
        List<TripLocationStat> topLocations,
        List<TravelMemory> favoriteMemories
) {
}
