package com.travelmemory.service;

import com.travelmemory.dto.CompanionSummary;
import com.travelmemory.entity.TripCompanion;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.travelmemory.vo.TripCompanionVO;

public interface TripCompanionService {

    List<TripCompanion> list(Long tripId);

    TripCompanion create(Long tripId, String name);

    TripCompanion create(Long tripId, String name, String avatarUrl, boolean isSelf);

    TripCompanion update(Long tripId, Long companionId, String name);

    TripCompanion update(Long tripId, Long companionId, String name, String avatarUrl, boolean isSelf);

    TripCompanion get(Long tripId, Long companionId);

    List<TripCompanionVO> listWithStats(Long tripId);

    TripCompanion setActive(Long tripId, Long companionId, boolean active);

    void replaceMemoryCompanions(Long memoryId, Long tripId, List<Long> companionIds);

    Map<Long, List<CompanionSummary>> findByMemoryIds(Set<Long> memoryIds);

    void deleteByMemoryId(Long memoryId);
}
