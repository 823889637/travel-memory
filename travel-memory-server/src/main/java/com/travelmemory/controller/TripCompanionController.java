package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.dto.CompanionActiveRequest;
import com.travelmemory.dto.CompanionSaveRequest;
import com.travelmemory.entity.TripCompanion;
import com.travelmemory.service.TripCompanionService;
import com.travelmemory.service.TravelMemoryService;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.vo.TripCompanionVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/companions")
public class TripCompanionController {

    private final TripCompanionService tripCompanionService;
    private final TravelMemoryService travelMemoryService;

    public TripCompanionController(TripCompanionService tripCompanionService,
            TravelMemoryService travelMemoryService) {
        this.tripCompanionService = tripCompanionService;
        this.travelMemoryService = travelMemoryService;
    }

    @GetMapping
    public Result<List<TripCompanionVO>> list(@PathVariable Long tripId) {
        return Result.success(tripCompanionService.listWithStats(tripId));
    }

    @PostMapping
    public Result<TripCompanion> create(@PathVariable Long tripId,
            @Valid @RequestBody CompanionSaveRequest request) {
        return Result.success(tripCompanionService.create(tripId, request.getName(), request.getAvatarUrl(),
                Boolean.TRUE.equals(request.getIsSelf())));
    }

    @PutMapping("/{companionId}")
    public Result<TripCompanion> update(@PathVariable Long tripId, @PathVariable Long companionId,
            @Valid @RequestBody CompanionSaveRequest request) {
        return Result.success(tripCompanionService.update(tripId, companionId, request.getName(),
                request.getAvatarUrl(), Boolean.TRUE.equals(request.getIsSelf())));
    }

    @PutMapping("/{companionId}/active")
    public Result<TripCompanion> setActive(@PathVariable Long tripId, @PathVariable Long companionId,
            @Valid @RequestBody CompanionActiveRequest request) {
        return Result.success(tripCompanionService.setActive(tripId, companionId, request.getActive()));
    }

    @GetMapping("/{companionId}/memories")
    public Result<List<TravelMemory>> memories(@PathVariable Long tripId, @PathVariable Long companionId) {
        tripCompanionService.get(tripId, companionId);
        return Result.success(travelMemoryService.timeline(tripId).stream()
                .filter(memory -> memory.getCompanionIds() != null && memory.getCompanionIds().contains(companionId))
                .toList());
    }
}
