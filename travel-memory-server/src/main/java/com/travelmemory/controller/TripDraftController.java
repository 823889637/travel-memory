package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.dto.TripDraftRequest;
import com.travelmemory.dto.TripDraftResponse;
import com.travelmemory.service.TripDraftService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trip-drafts")
public class TripDraftController {
    private final TripDraftService tripDraftService;

    public TripDraftController(TripDraftService tripDraftService) {
        this.tripDraftService = tripDraftService;
    }

    @GetMapping
    public Result<TripDraftResponse> get() {
        return Result.success(tripDraftService.get());
    }

    @PutMapping
    public Result<TripDraftResponse> save(@Valid @RequestBody TripDraftRequest request) {
        return Result.success(tripDraftService.save(request));
    }

    @DeleteMapping
    public Result<Void> delete() {
        tripDraftService.delete();
        return Result.success();
    }
}
