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
import com.travelmemory.service.ProtectedUploadReferenceService;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.vo.TravelTripListVO;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ProtectedUploadReferenceService uploadReferences;

    @Autowired
    public TravelTripServiceImpl(TravelTripMapper travelTripMapper, TravelMemoryMapper travelMemoryMapper,
            MemoryPhotoMapper memoryPhotoMapper, TripCompanionMapper tripCompanionMapper,
            MemoryCompanionMapper memoryCompanionMapper, CurrentUser currentUser,
            ProtectedUploadReferenceService uploadReferences) {
        this.travelTripMapper = travelTripMapper;
        this.travelMemoryMapper = travelMemoryMapper;
        this.memoryPhotoMapper = memoryPhotoMapper;
        this.tripCompanionMapper = tripCompanionMapper;
        this.memoryCompanionMapper = memoryCompanionMapper;
        this.currentUser = currentUser;
        this.uploadReferences = uploadReferences;
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
        List<Long> memoryIds = memories.stream().map(TravelMemory::getId).toList();
        List<MemoryPhoto> photos = memoryIds.isEmpty()
                ? List.of()
                : memoryPhotoMapper.selectList(new LambdaQueryWrapper<MemoryPhoto>()
                        .in(MemoryPhoto::getMemoryId, memoryIds)
                        .orderByAsc(MemoryPhoto::getMemoryId)
                        .orderByAsc(MemoryPhoto::getSortOrder)
                        .orderByAsc(MemoryPhoto::getId));

        Map<Long, Long> photoCountByMemory = new HashMap<>();
        Map<Long, String> firstPhotoByMemory = new HashMap<>();
        photos.forEach(photo -> {
            String photoUrl = normalizePhotoUrl(photo.getPhotoUrl());
            if (photoUrl != null) {
                photoCountByMemory.merge(photo.getMemoryId(), 1L, Long::sum);
                firstPhotoByMemory.putIfAbsent(photo.getMemoryId(), photoUrl);
            }
        });

        Map<Long, String> firstPhotoByTrip = new HashMap<>();
        Map<Long, Long> memoryCountByTrip = new HashMap<>();
        Map<Long, Long> photoCountByTrip = new HashMap<>();
        Map<Long, Set<String>> locationsByTrip = new HashMap<>();
        memories.forEach(memory -> {
            memoryCountByTrip.merge(memory.getTripId(), 1L, Long::sum);
            String photoUrl = normalizePhotoUrl(memory.getPhotoUrl());
            if (photoUrl == null) {
                photoUrl = firstPhotoByMemory.get(memory.getId());
            }
            if (photoUrl != null) {
                firstPhotoByTrip.putIfAbsent(memory.getTripId(), photoUrl);
            }

            long memoryPhotoCount = photoCountByMemory.getOrDefault(memory.getId(), 0L);
            if (memoryPhotoCount == 0 && photoUrl != null) {
                memoryPhotoCount = 1L;
            }
            photoCountByTrip.merge(memory.getTripId(), memoryPhotoCount, Long::sum);

            String locationName = normalizeLocationName(memory.getLocationName());
            if (locationName != null) {
                locationsByTrip.computeIfAbsent(memory.getTripId(), ignored -> new HashSet<>()).add(locationName);
            }
        });
        return trips.stream()
                .map(trip -> toListVO(
                        trip,
                        firstPhotoByTrip.get(trip.getId()),
                        memoryCountByTrip.get(trip.getId()),
                        photoCountByTrip.get(trip.getId()),
                        locationsByTrip.get(trip.getId())))
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
        validateTripFields(travelTrip);
        validateDateRange(travelTrip);
        travelTrip.setUserId(currentUser.requireId());
        travelTrip.setCoverPhotoUrl(normalizeOwnedCover(travelTrip.getCoverPhotoUrl()));
        travelTrip.setIsFavorite(false);
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
        validateTripFields(travelTrip);
        validateDateRange(travelTrip);
        travelTrip.setId(id);
        travelTrip.setUserId(existing.getUserId());
        travelTrip.setCoverPhotoUrl(existing.getCoverPhotoUrl());
        travelTrip.setIsFavorite(existing.getIsFavorite());
        travelTrip.setCreateTime(existing.getCreateTime());
        travelTrip.setUpdateTime(null);
        travelTrip.setDeleted(existing.getDeleted());
        travelTripMapper.updateById(travelTrip);
        travelTripMapper.update(null, new UpdateWrapper<TravelTrip>()
                .eq("id", id)
                .set("destination_latitude", travelTrip.getDestinationLatitude())
                .set("destination_longitude", travelTrip.getDestinationLongitude()));
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
    public TravelTrip setCoverUrl(Long tripId, String photoUrl) {
        TravelTrip travelTrip = getById(tripId);
        travelTrip.setCoverPhotoUrl(uploadReferences.requireOwnedImage(photoUrl));
        travelTripMapper.updateById(travelTrip);
        return getById(tripId);
    }

    @Override
    @Transactional
    public TravelTrip setFavorite(Long tripId, boolean favorite) {
        TravelTrip travelTrip = getById(tripId);
        travelTrip.setIsFavorite(favorite);
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

    private void validateTripFields(TravelTrip travelTrip) {
        if (travelTrip.getTitle() == null || travelTrip.getTitle().trim().isEmpty()) {
            throw new BusinessException(400, "Trip title is required");
        }
        if (travelTrip.getTitle().trim().length() > 100) {
            throw new BusinessException(400, "Trip title must not exceed 100 characters");
        }
        if (travelTrip.getDestination() != null && travelTrip.getDestination().length() > 100) {
            throw new BusinessException(400, "Destination must not exceed 100 characters");
        }
        validateDestinationCoordinates(travelTrip.getDestinationLatitude(), travelTrip.getDestinationLongitude());
        if (travelTrip.getDestinationLatitude() != null
                && (travelTrip.getDestination() == null || travelTrip.getDestination().trim().isEmpty())) {
            throw new BusinessException(400, "Destination name is required when destination coordinates are provided");
        }
        if (travelTrip.getDescription() != null && travelTrip.getDescription().length() > 500) {
            throw new BusinessException(400, "Description must not exceed 500 characters");
        }
        if (travelTrip.getNotes() != null && travelTrip.getNotes().length() > 1000) {
            throw new BusinessException(400, "Notes must not exceed 1000 characters");
        }
    }

    private void validateDestinationCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new BusinessException(400, "Destination latitude and longitude must be provided together");
        }
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new BusinessException(400, "Destination latitude must be between -90 and 90");
        }
        if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new BusinessException(400, "Destination longitude must be between -180 and 180");
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

    private String normalizeLocationName(String locationName) {
        if (locationName == null) {
            return null;
        }
        String normalized = locationName.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean samePhotoUrl(String first, String second) {
        String normalizedFirst = normalizePhotoUrl(first);
        String normalizedSecond = normalizePhotoUrl(second);
        return normalizedFirst != null && normalizedFirst.equals(normalizedSecond);
    }

    private TravelTripListVO toListVO(TravelTrip trip, String defaultCoverPhotoUrl, Long memoryCount,
            Long photoCount, Set<String> locations) {
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
        vo.setPhotoCount(photoCount == null ? 0L : photoCount);
        vo.setLocationCount(locations == null ? 0L : (long) locations.size());
        vo.setIsFavorite(Boolean.TRUE.equals(trip.getIsFavorite()));
        return vo;
    }

    private String normalizeOwnedCover(String photoUrl) {
        String normalized = normalizePhotoUrl(photoUrl);
        return normalized == null ? null : uploadReferences.requireOwnedImage(normalized);
    }
}
