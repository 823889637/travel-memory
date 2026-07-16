package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.dto.CitySearchResult;
import com.travelmemory.dto.CoordinateNormalizeRequest;
import com.travelmemory.dto.CoordinateNormalizeResult;
import com.travelmemory.dto.LocationSearchResult;
import com.travelmemory.dto.ReverseGeocodeResult;
import com.travelmemory.service.LocationService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/search")
    public Result<List<LocationSearchResult>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) String city
    ) {
        if (!StringUtils.hasText(keyword) || keyword.trim().length() > 80) {
            return Result.fail(400, "请输入不超过 80 个字符的地点关键词");
        }
        if ((latitude == null) != (longitude == null)) {
            return Result.fail(400, "latitude 和 longitude 需要同时提供");
        }
        return Result.success(locationService.search(keyword.trim(), latitude, longitude, city));
    }

    @GetMapping("/cities")
    public Result<List<CitySearchResult>> searchCities(@RequestParam String keyword) {
        if (!StringUtils.hasText(keyword) || keyword.trim().length() > 40) {
            return Result.fail(400, "请输入不超过 40 个字符的城市关键词");
        }
        return Result.success(locationService.searchCities(keyword.trim()));
    }

    @PostMapping("/normalize")
    public Result<CoordinateNormalizeResult> normalize(@Valid @RequestBody CoordinateNormalizeRequest request) {
        return Result.success(locationService.normalize(request));
    }
}
