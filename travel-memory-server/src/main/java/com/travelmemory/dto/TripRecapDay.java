package com.travelmemory.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import com.travelmemory.entity.TravelMemory;

public record TripRecapDay(
        int dayNumber,
        LocalDate date,
        long memoryCount,
        long photoCount,
        LocalTime earliestTime,
        LocalTime latestTime,
        TravelMemory representative,
        String locationSummary
) {
}
