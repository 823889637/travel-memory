package com.travelmemory.service;

import com.travelmemory.entity.TravelMemory;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface TravelMemoryService {

    List<TravelMemory> timeline(Long tripId);

    TravelMemory getById(Long id);

    TravelMemory create(TravelMemory travelMemory, MultipartFile photo);

    TravelMemory uploadPhoto(Long id, MultipartFile photo);

    TravelMemory update(Long id, TravelMemory travelMemory);

    void delete(Long id);
}
