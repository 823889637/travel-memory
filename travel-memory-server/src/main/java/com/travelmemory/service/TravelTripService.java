package com.travelmemory.service;

import com.travelmemory.entity.TravelTrip;
import com.travelmemory.vo.TravelTripListVO;
import java.util.List;

public interface TravelTripService {

    List<TravelTripListVO> listForHome();

    TravelTrip getById(Long id);

    TravelTrip create(TravelTrip travelTrip);

    TravelTrip update(Long id, TravelTrip travelTrip);

    TravelTrip setCover(Long tripId, Long memoryId);

    TravelTrip setCoverUrl(Long tripId, String photoUrl);

    TravelTrip setFavorite(Long tripId, boolean favorite);

    TravelTrip clearCover(Long tripId);

    void clearCoverIfMatches(Long tripId, String photoUrl);

    void replaceCoverIfMatches(Long tripId, String previousPhotoUrl, String nextPhotoUrl);

    void delete(Long id);
}
