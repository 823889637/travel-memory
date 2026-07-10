package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.common.StoredFile;
import com.travelmemory.dto.UploadResult;
import com.travelmemory.service.FileStorageService;
import com.travelmemory.service.TravelMemoryService;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.util.ImageMetadataExtractor;
import com.travelmemory.util.ImageMetadataInfo;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TravelMemoryServiceImpl extends ServiceImpl<TravelMemoryMapper, TravelMemory> implements TravelMemoryService {

    private final TravelMemoryMapper travelMemoryMapper;
    private final TravelTripService travelTripService;
    private final FileStorageService fileStorageService;
    private final ImageMetadataExtractor imageMetadataExtractor;

    public TravelMemoryServiceImpl(
            TravelMemoryMapper travelMemoryMapper,
            TravelTripService travelTripService,
            FileStorageService fileStorageService,
            ImageMetadataExtractor imageMetadataExtractor
    ) {
        this.travelMemoryMapper = travelMemoryMapper;
        this.travelTripService = travelTripService;
        this.fileStorageService = fileStorageService;
        this.imageMetadataExtractor = imageMetadataExtractor;
    }

    @Override
    public List<TravelMemory> timeline(Long tripId) {
        travelTripService.getById(tripId);
        return travelMemoryMapper.selectList(new LambdaQueryWrapper<TravelMemory>()
                .eq(TravelMemory::getTripId, tripId)
                .orderByAsc(TravelMemory::getRecordTime)
                .orderByAsc(TravelMemory::getCreateTime));
    }

    @Override
    public List<TravelMemory> search(Long tripId, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return List.of();
        }
        if (tripId != null) {
            travelTripService.getById(tripId);
        }

        LambdaQueryWrapper<TravelMemory> wrapper = new LambdaQueryWrapper<>();
        if (tripId != null) {
            wrapper.eq(TravelMemory::getTripId, tripId);
        }
        wrapper.and(query -> query
                .like(TravelMemory::getContent, normalizedKeyword)
                .or()
                .like(TravelMemory::getLocationName, normalizedKeyword))
                .orderByAsc(TravelMemory::getRecordTime)
                .orderByAsc(TravelMemory::getCreateTime);
        return travelMemoryMapper.selectList(wrapper);
    }

    @Override
    public TravelMemory getById(Long id) {
        TravelMemory travelMemory = travelMemoryMapper.selectById(id);
        if (travelMemory == null) {
            throw new BusinessException(404, "Memory not found");
        }
        return travelMemory;
    }

    @Override
    @Transactional
    public TravelMemory create(TravelMemory travelMemory, MultipartFile photo) {
        travelTripService.getById(travelMemory.getTripId());
        if (travelMemory.getIsFavorite() == null) {
            travelMemory.setIsFavorite(0);
        }

        if (photo != null && !photo.isEmpty()) {
            UploadResult uploadResult = uploadPhoto(photo);
            travelMemory.setPhotoUrl(uploadResult.getPhotoUrl());
            travelMemory.setPhotoPath(uploadResult.getPhotoPath());
            if (travelMemory.getRecordTime() == null && uploadResult.getPhotoTakenTime() != null) {
                travelMemory.setRecordTime(uploadResult.getPhotoTakenTime());
            }
            if (travelMemory.getLatitude() == null && uploadResult.getLatitude() != null) {
                travelMemory.setLatitude(uploadResult.getLatitude());
            }
            if (travelMemory.getLongitude() == null && uploadResult.getLongitude() != null) {
                travelMemory.setLongitude(uploadResult.getLongitude());
            }
        }
        if (travelMemory.getRecordTime() == null) {
            travelMemory.setRecordTime(LocalDateTime.now());
        }

        travelMemoryMapper.insert(travelMemory);
        return getById(travelMemory.getId());
    }

    @Override
    public UploadResult uploadPhoto(MultipartFile photo) {
        StoredFile storedFile = fileStorageService.store(photo);
        if (storedFile == null) {
            throw new BusinessException(400, "Photo file is required");
        }

        ImageMetadataInfo metadataInfo = imageMetadataExtractor.extract(storedFile.getPath());
        UploadResult result = new UploadResult();
        result.setPhotoUrl(storedFile.getUrl());
        result.setPhotoPath(storedFile.getPath());
        result.setPhotoTakenTime(metadataInfo.getPhotoTakenTime());
        result.setLatitude(metadataInfo.getLatitude());
        result.setLongitude(metadataInfo.getLongitude());
        result.setHasExifTime(metadataInfo.hasTime());
        result.setHasExifLocation(metadataInfo.hasLocation());
        return result;
    }

    @Override
    @Transactional
    public UploadResult uploadPhoto(Long id, MultipartFile photo) {
        TravelMemory travelMemory = getById(id);
        String previousPhotoUrl = travelMemory.getPhotoUrl();
        UploadResult uploadResult = uploadPhoto(photo);

        travelMemory.setPhotoUrl(uploadResult.getPhotoUrl());
        travelMemory.setPhotoPath(uploadResult.getPhotoPath());
        travelMemoryMapper.updateById(travelMemory);
        travelTripService.replaceCoverIfMatches(
                travelMemory.getTripId(),
                previousPhotoUrl,
                uploadResult.getPhotoUrl());
        return uploadResult;
    }

    @Override
    @Transactional
    public TravelMemory update(Long id, TravelMemory travelMemory) {
        TravelMemory existing = getById(id);
        travelMemory.setId(id);
        travelMemory.setTripId(existing.getTripId());
        travelMemory.setPhotoUrl(existing.getPhotoUrl());
        travelMemory.setPhotoPath(existing.getPhotoPath());
        travelMemory.setIsFavorite(existing.getIsFavorite());
        travelMemoryMapper.updateById(travelMemory);
        return getById(id);
    }

    @Override
    @Transactional
    public TravelMemory favorite(Long id, Boolean favorite) {
        TravelMemory travelMemory = getById(id);
        travelMemory.setIsFavorite(Boolean.TRUE.equals(favorite) ? 1 : 0);
        travelMemoryMapper.updateById(travelMemory);
        return getById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        TravelMemory travelMemory = getById(id);
        travelMemoryMapper.deleteById(id);
        travelTripService.clearCoverIfMatches(travelMemory.getTripId(), travelMemory.getPhotoUrl());
    }
}
