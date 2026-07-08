package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.dto.ReverseGeocodeResult;
import com.travelmemory.service.LocationService;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/reverse-geocode")
    public Result<ReverseGeocodeResult> reverseGeocode(
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude
    ) {
        return Result.success(locationService.reverseGeocode(latitude, longitude));
    }
}
