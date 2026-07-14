package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.entity.MemoryPhoto;
import com.travelmemory.entity.MemoryCompanion;
import com.travelmemory.entity.TripCompanion;
import com.travelmemory.entity.TravelTrip;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.mapper.MemoryPhotoMapper;
import com.travelmemory.mapper.MemoryCompanionMapper;
import com.travelmemory.mapper.TripCompanionMapper;
import com.travelmemory.mapper.TravelTripMapper;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.vo.TravelTripListVO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TravelTripServiceImpl extends ServiceImpl<TravelTripMapper, TravelTrip> implements TravelTripService {

    private final TravelTripMapper travelTripMapper;
    private final TravelMemoryMapper travelMemoryMapper;
    private final MemoryPhotoMapper memoryPhotoMapper;
    private final TripCompanionMapper tripCompanionMapper;
    private final MemoryCompanionMapper memoryCompanionMapper;
    private final CurrentUser currentUser;

    @Autowired
    public TravelTripServiceImpl(TravelTripMapper travelTripMapper, TravelMemoryMapper travelMemoryMapper,
            MemoryPhotoMapper memoryPhotoMapper, TripCompanionMapper tripCompanionMapper,
            MemoryCompanionMapper memoryCompanionMapper, CurrentUser currentUser) {
        this.travelTripMapper = travelTripMapper;
        this.travelMemoryMapper = travelMemoryMapper;
        this.memoryPhotoMapper = memoryPhotoMapper;
        this.tripCompanionMapper = tripCompanionMapper;
        this.memoryCompanionMapper = memoryCompanionMapper;
        this.currentUser = currentUser;
    }

    @Override
    public List<TravelTripListVO> listForHome() {
        List<TravelTrip> trips = travelTripMapper.selectList(new LambdaQueryWrapper<TravelTrip>()
                .eq(TravelTrip::getUserId, currentUser.requireId())
                .orderByDesc(TravelTrip::getStartDate)
                .orderByDesc(TravelTrip::getCreateTime));
        if (trips.isEmpty()) {
            return List.of();
        }

        List<Long> tripIds = trips.stream().map(TravelTrip::getId).toList();
        List<TravelMemory> memories = travelMemoryMapper.selectList(new LambdaQueryWrapper<TravelMemory>()
                .in(TravelMemory::getTripId, tripIds)
                .orderByAsc(TravelMemory::getRecordTime)
                .orderByAsc(TravelMemory::getCreateTime));
        Map<Long, String> firstPhotoByTrip = new HashMap<>();
        Map<Long, Long> memoryCountByTrip = new HashMap<>();
        memories.forEach(memory -> {
            memoryCountByTrip.merge(memory.getTripId(), 1L, Long::sum);
            String photoUrl = normalizePhotoUrl(memory.getPhotoUrl());
            if (photoUrl != null) {
                firstPhotoByTrip.putIfAbsent(memory.getTripId(), photoUrl);
            }
        });
        return trips.stream()
                .map(trip -> toListVO(trip, firstPhotoByTrip.get(trip.getId()), memoryCountByTrip.get(trip.getId())))
                .toList();
    }

    @Override
    public TravelTrip getById(Long id) {
        TravelTrip travelTrip = travelTripMapper.selectById(id);
        if (travelTrip == null || !currentUser.requireId().equals(travelTrip.getUserId())) {
            throw new BusinessException(404, "Trip not found");
        }
        return travelTrip;
    }

    @Override
    @Transactional
    public TravelTrip create(TravelTrip travelTrip) {
        validateDateRange(travelTrip);
        travelTrip.setUserId(currentUser.requireId());
        travelTrip.setCoverPhotoUrl(null);
        travelTrip.setCreateTime(null);
        travelTrip.setUpdateTime(null);
        travelTrip.setDeleted(null);
        travelTripMapper.insert(travelTrip);
        return getById(travelTrip.getId());
    }

    @Override
    @Transactional
    public TravelTrip update(Long id, TravelTrip travelTrip) {
        TravelTrip existing = getById(id);
        validateDateRange(travelTrip);
        travelTrip.setId(id);
        travelTrip.setUserId(existing.getUserId());
        travelTrip.setCoverPhotoUrl(existing.getCoverPhotoUrl());
        travelTrip.setCreateTime(existing.getCreateTime());
        travelTrip.setUpdateTime(null);
        travelTrip.setDeleted(existing.getDeleted());
        travelTripMapper.updateById(travelTrip);
        return getById(id);
    }

    @Override
    @Transactional
    public TravelTrip setCover(Long tripId, Long memoryId) {
        requirePositiveId(tripId, "tripId");
        requirePositiveId(memoryId, "memoryId");

        TravelTrip travelTrip = getById(tripId);
        TravelMemory memory = travelMemoryMapper.selectById(memoryId);
        if (memory == null) {
            throw new BusinessException(404, "Memory not found");
        }
        getById(memory.getTripId());
        if (!tripId.equals(memory.getTripId())) {
            throw new BusinessException(400, "Memory does not belong to this trip");
        }

        String photoUrl = normalizePhotoUrl(memory.getPhotoUrl());
        if (photoUrl == null) {
            throw new BusinessException(400, "Memory does not have a photo");
        }

        travelTrip.setCoverPhotoUrl(photoUrl);
        travelTripMapper.updateById(travelTrip);
        return getById(tripId);
    }

    @Override
    @Transactional
    public TravelTrip clearCover(Long tripId) {
        requirePositiveId(tripId, "tripId");
        TravelTrip travelTrip = getById(tripId);
        travelTrip.setCoverPhotoUrl(null);
        travelTripMapper.update(null, new UpdateWrapper<TravelTrip>()
                .eq("id", tripId)
                .set("cover_photo_url", null));
        return getById(tripId);
    }

    @Override
    @Transactional
    public void clearCoverIfMatches(Long tripId, String photoUrl) {
        String normalizedPhotoUrl = normalizePhotoUrl(photoUrl);
        if (tripId == null || normalizedPhotoUrl == null) {
            return;
        }

        TravelTrip travelTrip = getById(tripId);
        if (samePhotoUrl(travelTrip.getCoverPhotoUrl(), normalizedPhotoUrl)) {
            travelTrip.setCoverPhotoUrl(null);
            travelTripMapper.update(null, new UpdateWrapper<TravelTrip>()
                    .eq("id", tripId)
                    .set("cover_photo_url", null));
        }
    }

    @Override
    @Transactional
    public void replaceCoverIfMatches(Long tripId, String previousPhotoUrl, String nextPhotoUrl) {
        String normalizedPreviousUrl = normalizePhotoUrl(previousPhotoUrl);
        String normalizedNextUrl = normalizePhotoUrl(nextPhotoUrl);
        if (tripId == null || normalizedPreviousUrl == null || normalizedNextUrl == null) {
            return;
        }

        TravelTrip travelTrip = getById(tripId);
        if (samePhotoUrl(travelTrip.getCoverPhotoUrl(), normalizedPreviousUrl)) {
            travelTrip.setCoverPhotoUrl(normalizedNextUrl);
            travelTripMapper.update(null, new UpdateWrapper<TravelTrip>()
                    .eq("id", tripId)
                    .set("cover_photo_url", normalizedNextUrl));
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getById(id);
        List<TravelMemory> memories = travelMemoryMapper.selectList(new LambdaQueryWrapper<TravelMemory>()
                .eq(TravelMemory::getTripId, id));
        if (!memories.isEmpty()) {
            List<Long> memoryIds = memories.stream().map(TravelMemory::getId).toList();
            memoryCompanionMapper.delete(new LambdaQueryWrapper<MemoryCompanion>()
                    .in(MemoryCompanion::getMemoryId, memoryIds));
            memoryPhotoMapper.delete(new LambdaQueryWrapper<MemoryPhoto>().in(MemoryPhoto::getMemoryId, memoryIds));
            travelMemoryMapper.delete(new LambdaQueryWrapper<TravelMemory>().in(TravelMemory::getId, memoryIds));
        }
        List<TripCompanion> companions = tripCompanionMapper.selectList(new LambdaQueryWrapper<TripCompanion>()
                .eq(TripCompanion::getTripId, id));
        if (!companions.isEmpty()) {
            List<Long> companionIds = companions.stream().map(TripCompanion::getId).toList();
            memoryCompanionMapper.delete(new LambdaQueryWrapper<MemoryCompanion>()
                    .in(MemoryCompanion::getCompanionId, companionIds));
            tripCompanionMapper.delete(new LambdaQueryWrapper<TripCompanion>()
                    .in(TripCompanion::getId, companionIds));
        }
        travelTripMapper.deleteById(id);
    }

    private void validateDateRange(TravelTrip travelTrip) {
        if (travelTrip.getStartDate() == null || travelTrip.getEndDate() == null) {
            return;
        }
        if (travelTrip.getEndDate().isBefore(travelTrip.getStartDate())) {
            throw new BusinessException(400, "End date cannot be earlier than start date");
        }
    }

    private void requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, fieldName + " must be positive");
        }
    }

    private String normalizePhotoUrl(String photoUrl) {
        if (photoUrl == null) {
            return null;
        }
        String normalized = photoUrl.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean samePhotoUrl(String first, String second) {
        String normalizedFirst = normalizePhotoUrl(first);
        String normalizedSecond = normalizePhotoUrl(second);
        return normalizedFirst != null && normalizedFirst.equals(normalizedSecond);
    }

    private TravelTripListVO toListVO(TravelTrip trip, String defaultCoverPhotoUrl, Long memoryCount) {
        TravelTripListVO vo = new TravelTripListVO();
        vo.setId(trip.getId());
        vo.setTitle(trip.getTitle());
        vo.setDescription(trip.getDescription());
        vo.setDestination(trip.getDestination());
        vo.setStartDate(trip.getStartDate());
        vo.setEndDate(trip.getEndDate());
        String explicitCoverPhotoUrl = normalizePhotoUrl(trip.getCoverPhotoUrl());
        vo.setCoverPhotoUrl(explicitCoverPhotoUrl);
        vo.setEffectiveCoverPhotoUrl(explicitCoverPhotoUrl != null ? explicitCoverPhotoUrl : defaultCoverPhotoUrl);
        vo.setMemoryCount(memoryCount == null ? 0L : memoryCount);
        return vo;
    }
}
