package com.travelmemory.service.impl;

import com.travelmemory.dto.TripLocationStat;
import com.travelmemory.dto.TripRecapDay;
import com.travelmemory.dto.TripRecapResponse;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.entity.TravelTrip;
import com.travelmemory.service.TravelMemoryService;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.service.TripRecapService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TripRecapServiceImpl implements TripRecapService {
    private final TravelTripService travelTripService;
    private final TravelMemoryService travelMemoryService;

    public TripRecapServiceImpl(TravelTripService travelTripService, TravelMemoryService travelMemoryService) {
        this.travelTripService = travelTripService;
        this.travelMemoryService = travelMemoryService;
    }

    @Override
    public TripRecapResponse get(Long tripId) {
        TravelTrip trip = travelTripService.getById(tripId);
        List<TravelMemory> memories = travelMemoryService.timeline(tripId);
        Map<LocalDate, List<TravelMemory>> grouped = new LinkedHashMap<>();
        for (TravelMemory memory : memories) {
            if (memory.getRecordTime() != null) {
                grouped.computeIfAbsent(memory.getRecordTime().toLocalDate(), ignored -> new ArrayList<>()).add(memory);
            }
        }

        LocalDate dayBase = trip.getStartDate();
        if (dayBase == null && !grouped.isEmpty()) dayBase = grouped.keySet().iterator().next();
        List<TripRecapDay> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<TravelMemory>> entry : grouped.entrySet()) {
            List<TravelMemory> dayMemories = entry.getValue();
            int dayNumber = dayBase == null ? days.size() + 1
                    : Math.max(1, (int) ChronoUnit.DAYS.between(dayBase, entry.getKey()) + 1);
            long photoCount = dayMemories.stream().mapToLong(this::photoCount).sum();
            TravelMemory representative = dayMemories.stream().filter(memory -> hasText(memory.getPhotoUrl()))
                    .findFirst().orElse(dayMemories.get(0));
            String locationSummary = String.join(" · ", dayMemories.stream()
                    .map(TravelMemory::getLocationName).filter(this::hasText)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
            days.add(new TripRecapDay(dayNumber, entry.getKey(), dayMemories.size(), photoCount,
                    dayMemories.get(0).getRecordTime().toLocalTime(),
                    dayMemories.get(dayMemories.size() - 1).getRecordTime().toLocalTime(),
                    representative, locationSummary));
        }

        Map<String, Long> locationCounts = new LinkedHashMap<>();
        memories.stream().map(TravelMemory::getLocationName).filter(this::hasText)
                .forEach(name -> locationCounts.merge(name.trim(), 1L, Long::sum));
        List<TripLocationStat> topLocations = locationCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(8).map(entry -> new TripLocationStat(entry.getKey(), entry.getValue())).toList();
        List<TravelMemory> allFavorites = memories.stream()
                .filter(memory -> Integer.valueOf(1).equals(memory.getIsFavorite()))
                .sorted(Comparator.comparing(TravelMemory::getRecordTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        long tripDays = calculateTripDays(trip, grouped);
        long photoCount = memories.stream().mapToLong(this::photoCount).sum();
        long located = memories.stream().filter(memory -> memory.getLatitude() != null && memory.getLongitude() != null).count();
        return new TripRecapResponse(trip, tripDays, memories.size(), photoCount, allFavorites.size(), located,
                locationCounts.size(), days, topLocations, allFavorites.stream().limit(8).toList());
    }

    private long calculateTripDays(TravelTrip trip, Map<LocalDate, List<TravelMemory>> grouped) {
        if (trip.getStartDate() != null && trip.getEndDate() != null) {
            return Math.max(1, ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1);
        }
        return Math.max(1, grouped.size());
    }

    private long photoCount(TravelMemory memory) {
        if (memory.getPhotoCount() != null) return memory.getPhotoCount();
        return hasText(memory.getPhotoUrl()) ? 1 : 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
