package com.travelmemory.service;

import com.travelmemory.dto.TripRecapResponse;

public interface TripRecapService {
    TripRecapResponse get(Long tripId);
}
