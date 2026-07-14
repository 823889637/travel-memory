package com.travelmemory.service;

import com.travelmemory.dto.CompanionSummary;
import com.travelmemory.entity.TripCompanion;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TripCompanionService {

    List<TripCompanion> list(Long tripId);

    TripCompanion create(Long tripId, String name);

    TripCompanion update(Long tripId, Long companionId, String name);

    TripCompanion setActive(Long tripId, Long companionId, boolean active);

    void replaceMemoryCompanions(Long memoryId, Long tripId, List<Long> companionIds);

    Map<Long, List<CompanionSummary>> findByMemoryIds(Set<Long> memoryIds);

    void deleteByMemoryId(Long memoryId);
}
