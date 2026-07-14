package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmemory.dto.CompanionSummary;
import com.travelmemory.entity.MemoryCompanion;
import com.travelmemory.entity.TripCompanion;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.MemoryCompanionMapper;
import com.travelmemory.mapper.TripCompanionMapper;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.service.TripCompanionService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripCompanionServiceImpl implements TripCompanionService {

    private static final int MAX_COMPANIONS = 20;

    private final TripCompanionMapper tripCompanionMapper;
    private final MemoryCompanionMapper memoryCompanionMapper;
    private final TravelTripService travelTripService;

    public TripCompanionServiceImpl(TripCompanionMapper tripCompanionMapper,
            MemoryCompanionMapper memoryCompanionMapper, TravelTripService travelTripService) {
        this.tripCompanionMapper = tripCompanionMapper;
        this.memoryCompanionMapper = memoryCompanionMapper;
        this.travelTripService = travelTripService;
    }

    @Override
    public List<TripCompanion> list(Long tripId) {
        travelTripService.getById(tripId);
        return tripCompanionMapper.selectList(new LambdaQueryWrapper<TripCompanion>()
                .eq(TripCompanion::getTripId, tripId)
                .orderByDesc(TripCompanion::getActive)
                .orderByAsc(TripCompanion::getSortOrder)
                .orderByAsc(TripCompanion::getId));
    }

    @Override
    @Transactional
    public TripCompanion create(Long tripId, String name) {
        travelTripService.getById(tripId);
        String normalizedName = normalizeName(name);
        ensureUniqueName(tripId, normalizedName, null);
        long activeCount = tripCompanionMapper.selectCount(new LambdaQueryWrapper<TripCompanion>()
                .eq(TripCompanion::getTripId, tripId)
                .eq(TripCompanion::getActive, true));
        if (activeCount >= MAX_COMPANIONS) {
            throw new BusinessException(400, "A trip can contain at most " + MAX_COMPANIONS + " companions");
        }

        TripCompanion companion = new TripCompanion();
        companion.setTripId(tripId);
        companion.setName(normalizedName);
        companion.setSortOrder((int) activeCount);
        companion.setActive(true);
        tripCompanionMapper.insert(companion);
        return requireOwnedCompanion(tripId, companion.getId());
    }

    @Override
    @Transactional
    public TripCompanion update(Long tripId, Long companionId, String name) {
        TripCompanion companion = requireOwnedCompanion(tripId, companionId);
        String normalizedName = normalizeName(name);
        ensureUniqueName(tripId, normalizedName, companionId);
        companion.setName(normalizedName);
        tripCompanionMapper.updateById(companion);
        return requireOwnedCompanion(tripId, companionId);
    }

    @Override
    @Transactional
    public TripCompanion setActive(Long tripId, Long companionId, boolean active) {
        TripCompanion companion = requireOwnedCompanion(tripId, companionId);
        if (active && !Boolean.TRUE.equals(companion.getActive())) {
            long activeCount = tripCompanionMapper.selectCount(new LambdaQueryWrapper<TripCompanion>()
                    .eq(TripCompanion::getTripId, tripId)
                    .eq(TripCompanion::getActive, true));
            if (activeCount >= MAX_COMPANIONS) {
                throw new BusinessException(400, "A trip can contain at most " + MAX_COMPANIONS + " companions");
            }
        }
        companion.setActive(active);
        tripCompanionMapper.updateById(companion);
        return requireOwnedCompanion(tripId, companionId);
    }

    @Override
    @Transactional
    public void replaceMemoryCompanions(Long memoryId, Long tripId, List<Long> companionIds) {
        travelTripService.getById(tripId);
        List<Long> normalizedIds = companionIds == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(companionIds));
        if (normalizedIds.size() > MAX_COMPANIONS) {
            throw new BusinessException(400, "A memory contains too many companions");
        }
        if (!normalizedIds.isEmpty()) {
            List<TripCompanion> companions = tripCompanionMapper.selectList(new LambdaQueryWrapper<TripCompanion>()
                    .eq(TripCompanion::getTripId, tripId)
                    .in(TripCompanion::getId, normalizedIds));
            if (companions.size() != normalizedIds.size()
                    || companions.stream().anyMatch(companion -> !tripId.equals(companion.getTripId()))) {
                throw new BusinessException(404, "Companion not found");
            }
        }

        deleteByMemoryId(memoryId);
        for (Long companionId : normalizedIds) {
            MemoryCompanion relation = new MemoryCompanion();
            relation.setMemoryId(memoryId);
            relation.setCompanionId(companionId);
            memoryCompanionMapper.insert(relation);
        }
    }

    @Override
    public Map<Long, List<CompanionSummary>> findByMemoryIds(Set<Long> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return Map.of();
        }
        List<MemoryCompanion> relations = memoryCompanionMapper.selectList(
                new LambdaQueryWrapper<MemoryCompanion>().in(MemoryCompanion::getMemoryId, memoryIds));
        if (relations.isEmpty()) {
            return Map.of();
        }

        Set<Long> companionIds = new LinkedHashSet<>();
        relations.forEach(relation -> companionIds.add(relation.getCompanionId()));
        Map<Long, TripCompanion> companionsById = new HashMap<>();
        tripCompanionMapper.selectList(new LambdaQueryWrapper<TripCompanion>()
                .in(TripCompanion::getId, companionIds))
                .forEach(companion -> companionsById.put(companion.getId(), companion));

        Map<Long, List<TripCompanion>> grouped = new HashMap<>();
        for (MemoryCompanion relation : relations) {
            TripCompanion companion = companionsById.get(relation.getCompanionId());
            if (companion != null) {
                grouped.computeIfAbsent(relation.getMemoryId(), ignored -> new ArrayList<>()).add(companion);
            }
        }

        Comparator<TripCompanion> order = Comparator
                .comparing(TripCompanion::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TripCompanion::getId);
        Map<Long, List<CompanionSummary>> result = new HashMap<>();
        grouped.forEach((memoryId, companions) -> {
            companions.sort(order);
            result.put(memoryId, companions.stream()
                    .map(companion -> new CompanionSummary(companion.getId(), companion.getName()))
                    .toList());
        });
        return result;
    }

    @Override
    public void deleteByMemoryId(Long memoryId) {
        memoryCompanionMapper.delete(new LambdaQueryWrapper<MemoryCompanion>()
                .eq(MemoryCompanion::getMemoryId, memoryId));
    }

    private TripCompanion requireOwnedCompanion(Long tripId, Long companionId) {
        travelTripService.getById(tripId);
        TripCompanion companion = tripCompanionMapper.selectById(companionId);
        if (companion == null || !tripId.equals(companion.getTripId())) {
            throw new BusinessException(404, "Companion not found");
        }
        return companion;
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(400, "Companion name is required");
        }
        if (normalized.length() > 50) {
            throw new BusinessException(400, "Companion name cannot exceed 50 characters");
        }
        return normalized;
    }

    private void ensureUniqueName(Long tripId, String name, Long excludedId) {
        LambdaQueryWrapper<TripCompanion> query = new LambdaQueryWrapper<TripCompanion>()
                .eq(TripCompanion::getTripId, tripId)
                .eq(TripCompanion::getName, name);
        if (excludedId != null) {
            query.ne(TripCompanion::getId, excludedId);
        }
        if (tripCompanionMapper.selectCount(query) > 0) {
            throw new BusinessException(400, "Companion name already exists");
        }
    }
}
