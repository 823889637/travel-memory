package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.dto.UploadResult;
import com.travelmemory.dto.MemoryPhotoOrderRequest;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.service.TravelMemoryService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/memories")
public class TravelMemoryController {

    private final TravelMemoryService travelMemoryService;

    public TravelMemoryController(TravelMemoryService travelMemoryService) {
        this.travelMemoryService = travelMemoryService;
    }

    @GetMapping("/timeline")
    public Result<List<TravelMemory>> timeline(@RequestParam Long tripId) {
        return Result.success(travelMemoryService.timeline(tripId));
    }

    @GetMapping("/search")
    public Result<List<TravelMemory>> search(
            @RequestParam(required = false) Long tripId,
            @RequestParam String keyword
    ) {
        return Result.success(travelMemoryService.search(tripId, keyword));
    }

    @PostMapping
    public Result<TravelMemory> create(
            @RequestParam Long tripId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) String locationName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordTime,
            @RequestParam(name = "photoUrl", required = false) List<String> photoUrls,
            @RequestParam(name = "photoPath", required = false) List<String> photoPaths,
            @RequestParam(name = "photo", required = false) List<MultipartFile> photos
    ) {
        TravelMemory travelMemory = new TravelMemory();
        travelMemory.setTripId(tripId);
        travelMemory.setContent(content);
        if (photoUrls != null) {
            List<com.travelmemory.entity.MemoryPhoto> existingPhotos = new ArrayList<>();
            for (int index = 0; index < photoUrls.size(); index++) {
                com.travelmemory.entity.MemoryPhoto memoryPhoto = new com.travelmemory.entity.MemoryPhoto();
                memoryPhoto.setPhotoUrl(photoUrls.get(index));
                memoryPhoto.setPhotoPath(photoPaths != null && index < photoPaths.size() ? photoPaths.get(index) : null);
                existingPhotos.add(memoryPhoto);
            }
            travelMemory.setPhotos(existingPhotos);
        }
        travelMemory.setLatitude(latitude);
        travelMemory.setLongitude(longitude);
        travelMemory.setLocationName(locationName);
        travelMemory.setRecordTime(recordTime);
        return Result.success(travelMemoryService.create(travelMemory, photos));
    }

    @PostMapping("/photo")
    public Result<UploadResult> uploadPhoto(@RequestParam MultipartFile photo) {
        return Result.success(travelMemoryService.uploadPhoto(photo));
    }

    @PostMapping("/{id}/photo")
    public Result<UploadResult> uploadPhoto(@PathVariable Long id, @RequestParam MultipartFile photo) {
        return Result.success(travelMemoryService.uploadPhoto(id, photo));
    }

    @PostMapping("/{id}/photos")
    public Result<TravelMemory> addPhoto(@PathVariable Long id, @RequestParam MultipartFile photo) {
        return Result.success(travelMemoryService.addPhoto(id, photo));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public Result<TravelMemory> deletePhoto(@PathVariable Long id, @PathVariable Long photoId) {
        return Result.success(travelMemoryService.deletePhoto(id, photoId));
    }

    @PutMapping("/{id}/photos/order")
    public Result<TravelMemory> reorderPhotos(
            @PathVariable Long id,
            @Valid @RequestBody MemoryPhotoOrderRequest request
    ) {
        return Result.success(travelMemoryService.reorderPhotos(id, request.getPhotoIds()));
    }

    @GetMapping("/{id}")
    public Result<TravelMemory> getById(@PathVariable Long id) {
        return Result.success(travelMemoryService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<TravelMemory> update(@PathVariable Long id, @Valid @RequestBody TravelMemory travelMemory) {
        return Result.success(travelMemoryService.update(id, travelMemory));
    }

    @PutMapping("/{id}/favorite")
    public Result<TravelMemory> favorite(@PathVariable Long id, @RequestParam Boolean favorite) {
        return Result.success(travelMemoryService.favorite(id, favorite));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        travelMemoryService.delete(id);
        return Result.success();
    }
}
