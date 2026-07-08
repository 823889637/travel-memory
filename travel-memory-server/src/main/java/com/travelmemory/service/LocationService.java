package com.travelmemory.service;

import com.travelmemory.dto.ReverseGeocodeResult;
import java.math.BigDecimal;

public interface LocationService {

    ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude);
}
