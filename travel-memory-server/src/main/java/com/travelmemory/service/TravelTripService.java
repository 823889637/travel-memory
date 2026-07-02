package com.travelmemory.service;

import com.travelmemory.entity.TravelTrip;
import com.travelmemory.vo.TravelTripListVO;
import java.util.List;

public interface TravelTripService {

    List<TravelTripListVO> listForHome();

    TravelTrip getById(Long id);

    TravelTrip create(TravelTrip travelTrip);

    TravelTrip update(Long id, TravelTrip travelTrip);

    void delete(Long id);
}
