package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.common.StoredFile;
import com.travelmemory.service.FileStorageService;
import com.travelmemory.service.TravelMemoryService;
import com.travelmemory.service.TravelTripService;
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

    public TravelMemoryServiceImpl(
            TravelMemoryMapper travelMemoryMapper,
            TravelTripService travelTripService,
            FileStorageService fileStorageService
    ) {
        this.travelMemoryMapper = travelMemoryMapper;
        this.travelTripService = travelTripService;
        this.fileStorageService = fileStorageService;
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
        if (travelMemory.getRecordTime() == null) {
            travelMemory.setRecordTime(LocalDateTime.now());
        }

        StoredFile storedFile = fileStorageService.store(photo);
        if (storedFile != null) {
            travelMemory.setPhotoUrl(storedFile.getUrl());
            travelMemory.setPhotoPath(storedFile.getPath());
        }

        travelMemoryMapper.insert(travelMemory);
        return getById(travelMemory.getId());
    }

    @Override
    @Transactional
    public TravelMemory uploadPhoto(Long id, MultipartFile photo) {
        TravelMemory travelMemory = getById(id);
        StoredFile storedFile = fileStorageService.store(photo);
        if (storedFile == null) {
            throw new BusinessException(400, "Photo file is required");
        }

        travelMemory.setPhotoUrl(storedFile.getUrl());
        travelMemory.setPhotoPath(storedFile.getPath());
        travelMemoryMapper.updateById(travelMemory);
        return getById(id);
    }

    @Override
    @Transactional
    public TravelMemory update(Long id, TravelMemory travelMemory) {
        TravelMemory existing = getById(id);
        travelMemory.setId(id);
        travelMemory.setTripId(existing.getTripId());
        travelMemoryMapper.updateById(travelMemory);
        return getById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getById(id);
        travelMemoryMapper.deleteById(id);
    }
}
