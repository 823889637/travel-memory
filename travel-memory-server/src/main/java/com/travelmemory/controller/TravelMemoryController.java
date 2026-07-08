package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.dto.UploadResult;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.service.TravelMemoryService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
            @RequestParam(required = false) String photoUrl,
            @RequestParam(required = false) String photoPath,
            @RequestParam(required = false) MultipartFile photo
    ) {
        TravelMemory travelMemory = new TravelMemory();
        travelMemory.setTripId(tripId);
        travelMemory.setContent(content);
        travelMemory.setPhotoUrl(photoUrl);
        travelMemory.setPhotoPath(photoPath);
        travelMemory.setLatitude(latitude);
        travelMemory.setLongitude(longitude);
        travelMemory.setLocationName(locationName);
        travelMemory.setRecordTime(recordTime);
        return Result.success(travelMemoryService.create(travelMemory, photo));
    }

    @PostMapping("/photo")
    public Result<UploadResult> uploadPhoto(@RequestParam MultipartFile photo) {
        return Result.success(travelMemoryService.uploadPhoto(photo));
    }

    @PostMapping("/{id}/photo")
    public Result<UploadResult> uploadPhoto(@PathVariable Long id, @RequestParam MultipartFile photo) {
        return Result.success(travelMemoryService.uploadPhoto(id, photo));
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
