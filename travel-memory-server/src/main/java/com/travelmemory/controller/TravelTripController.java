package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.entity.TravelTrip;
import com.travelmemory.service.TravelTripService;
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

    public TravelTripController(TravelTripService travelTripService) {
        this.travelTripService = travelTripService;
    }

    @GetMapping
    public Result<List<TravelTripListVO>> list() {
        return Result.success(travelTripService.listForHome());
    }

    @GetMapping("/{id}")
    public Result<TravelTrip> getById(@PathVariable Long id) {
        return Result.success(travelTripService.getById(id));
    }

    @PostMapping
    public Result<TravelTrip> create(@Valid @RequestBody TravelTrip travelTrip) {
        return Result.success(travelTripService.create(travelTrip));
    }

    @PutMapping("/{id}")
    public Result<TravelTrip> update(@PathVariable Long id, @Valid @RequestBody TravelTrip travelTrip) {
        return Result.success(travelTripService.update(id, travelTrip));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        travelTripService.delete(id);
        return Result.success();
    }
}
