package com.travelmemory.service;

import com.travelmemory.dto.TripDraftRequest;
import com.travelmemory.dto.TripDraftResponse;

public interface TripDraftService {
    TripDraftResponse get();
    TripDraftResponse save(TripDraftRequest request);
    void delete();
}
