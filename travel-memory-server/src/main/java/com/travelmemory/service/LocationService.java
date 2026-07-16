package com.travelmemory.service;

import com.travelmemory.dto.CitySearchResult;
import com.travelmemory.dto.ReverseGeocodeResult;
import com.travelmemory.dto.CoordinateNormalizeRequest;
import com.travelmemory.dto.CoordinateNormalizeResult;
import com.travelmemory.dto.LocationSearchResult;
import java.math.BigDecimal;
import java.util.List;

public interface LocationService {

    ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude);

    List<LocationSearchResult> search(String keyword, BigDecimal latitude, BigDecimal longitude, String city);

    List<CitySearchResult> searchCities(String keyword);

    CoordinateNormalizeResult normalize(CoordinateNormalizeRequest request);
}
