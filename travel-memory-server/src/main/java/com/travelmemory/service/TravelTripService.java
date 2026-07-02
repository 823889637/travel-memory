package com.travelmemory.service;

import com.travelmemory.entity.TravelTrip;
import java.util.List;

public interface TravelTripService {

    List<TravelTrip> list();

    TravelTrip getById(Long id);

    TravelTrip create(TravelTrip travelTrip);

    TravelTrip update(Long id, TravelTrip travelTrip);

    void delete(Long id);
}
