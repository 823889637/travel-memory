package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.dto.CompanionActiveRequest;
import com.travelmemory.dto.CompanionSaveRequest;
import com.travelmemory.entity.TripCompanion;
import com.travelmemory.service.TripCompanionService;
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

    public TripCompanionController(TripCompanionService tripCompanionService) {
        this.tripCompanionService = tripCompanionService;
    }

    @GetMapping
    public Result<List<TripCompanion>> list(@PathVariable Long tripId) {
        return Result.success(tripCompanionService.list(tripId));
    }

    @PostMapping
    public Result<TripCompanion> create(@PathVariable Long tripId,
            @Valid @RequestBody CompanionSaveRequest request) {
        return Result.success(tripCompanionService.create(tripId, request.getName()));
    }

    @PutMapping("/{companionId}")
    public Result<TripCompanion> update(@PathVariable Long tripId, @PathVariable Long companionId,
            @Valid @RequestBody CompanionSaveRequest request) {
        return Result.success(tripCompanionService.update(tripId, companionId, request.getName()));
    }

    @PutMapping("/{companionId}/active")
    public Result<TripCompanion> setActive(@PathVariable Long tripId, @PathVariable Long companionId,
            @Valid @RequestBody CompanionActiveRequest request) {
        return Result.success(tripCompanionService.setActive(tripId, companionId, request.getActive()));
    }
}
