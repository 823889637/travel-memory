package com.travelmemory.service;

import com.travelmemory.entity.TravelMemory;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface TravelMemoryService {

    List<TravelMemory> timeline(Long tripId);

    List<TravelMemory> search(Long tripId, String keyword);

    TravelMemory getById(Long id);

    TravelMemory create(TravelMemory travelMemory, MultipartFile photo);

    TravelMemory uploadPhoto(Long id, MultipartFile photo);

    TravelMemory update(Long id, TravelMemory travelMemory);

    TravelMemory favorite(Long id, Boolean favorite);

    void delete(Long id);
}
