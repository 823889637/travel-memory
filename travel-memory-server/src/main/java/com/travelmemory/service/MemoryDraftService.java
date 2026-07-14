package com.travelmemory.service;

import com.travelmemory.dto.MemoryDraftRequest;
import com.travelmemory.dto.MemoryDraftResponse;

public interface MemoryDraftService {
    MemoryDraftResponse get(Long tripId, Long memoryId);
    MemoryDraftResponse save(MemoryDraftRequest request);
    void delete(Long tripId, Long memoryId);
}
