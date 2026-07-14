package com.travelmemory.service;

import com.travelmemory.entity.TravelMemory;
import com.travelmemory.dto.UploadResult;
import com.travelmemory.dto.MemoryUpdateRequest;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface TravelMemoryService {

    List<TravelMemory> timeline(Long tripId);

    List<TravelMemory> search(Long tripId, String keyword);

    TravelMemory getById(Long id);

    TravelMemory create(TravelMemory travelMemory, List<MultipartFile> photos);

    UploadResult uploadPhoto(MultipartFile photo);

    UploadResult uploadPhoto(Long id, MultipartFile photo);

    TravelMemory addPhoto(Long id, MultipartFile photo);

    TravelMemory deletePhoto(Long id, Long photoId);

    TravelMemory reorderPhotos(Long id, List<Long> photoIds);

    TravelMemory update(Long id, MemoryUpdateRequest request);

    TravelMemory favorite(Long id, Boolean favorite);

    void delete(Long id);
}
