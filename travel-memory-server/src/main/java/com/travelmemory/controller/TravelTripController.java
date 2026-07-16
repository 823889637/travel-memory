package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.dto.TripCoverRequest;
import com.travelmemory.dto.TripCoverUrlRequest;
import com.travelmemory.dto.TripCreateRequest;
import com.travelmemory.dto.TripFavoriteRequest;
import com.travelmemory.dto.TripUpdateRequest;
import com.travelmemory.dto.TripRecapResponse;
import com.travelmemory.entity.TravelTrip;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.service.TripRecapService;
import com.travelmemory.vo.TravelTripListVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
public class TravelTripController {

    private final TravelTripService travelTripService;
    private final TripRecapService tripRecapService;

    public TravelTripController(TravelTripService travelTripService, TripRecapService tripRecapService) {
        this.travelTripService = travelTripService;
        this.tripRecapService = tripRecapService;
    }

    @GetMapping
    public Result<List<TravelTripListVO>> list() {
        return Result.success(travelTripService.listForHome());
    }

    @GetMapping("/{id}")
    public Result<TravelTrip> getById(@PathVariable Long id) {
        return Result.success(travelTripService.getById(id));
    }

    @GetMapping("/{id}/recap")
    public Result<TripRecapResponse> recap(@PathVariable Long id) {
        return Result.success(tripRecapService.get(id));
    }

    @PostMapping
    public Result<TravelTrip> create(@Valid @RequestBody TripCreateRequest request) {
        return Result.success(travelTripService.create(toTravelTrip(request)));
    }

    @PutMapping("/{id}")
    public Result<TravelTrip> update(@PathVariable Long id, @Valid @RequestBody TripUpdateRequest request) {
        return Result.success(travelTripService.update(id, toTravelTrip(request)));
    }

    @PutMapping("/{id}/cover")
    public Result<TravelTrip> setCover(@PathVariable Long id, @Valid @RequestBody TripCoverRequest request) {
        return Result.success(travelTripService.setCover(id, request.getMemoryId()));
    }

    @PutMapping("/{id}/cover-url")
    public Result<TravelTrip> setCoverUrl(@PathVariable Long id, @Valid @RequestBody TripCoverUrlRequest request) {
        return Result.success(travelTripService.setCoverUrl(id, request.getPhotoUrl()));
    }

    @PutMapping("/{id}/favorite")
    public Result<TravelTrip> setFavorite(@PathVariable Long id, @Valid @RequestBody TripFavoriteRequest request) {
        return Result.success(travelTripService.setFavorite(id, Boolean.TRUE.equals(request.getFavorite())));
    }

    @DeleteMapping("/{id}/cover")
    public Result<TravelTrip> clearCover(@PathVariable Long id) {
        return Result.success(travelTripService.clearCover(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        travelTripService.delete(id);
        return Result.success();
    }

    private TravelTrip toTravelTrip(TripCreateRequest request) {
        TravelTrip trip = new TravelTrip();
        trip.setTitle(request.getTitle().trim());
        trip.setDestination(normalizeNullable(request.getDestination()));
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setDescription(normalizeNullable(request.getDescription()));
        trip.setNotes(normalizeNullable(request.getNotes()));
        trip.setCoverPhotoUrl(normalizeNullable(request.getCoverPhotoUrl()));
        return trip;
    }

    private TravelTrip toTravelTrip(TripUpdateRequest request) {
        TravelTrip trip = new TravelTrip();
        trip.setTitle(request.getTitle().trim());
        trip.setDestination(normalizeNullable(request.getDestination()));
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setDescription(normalizeNullable(request.getDescription()));
        trip.setNotes(normalizeNullable(request.getNotes()));
        return trip;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
