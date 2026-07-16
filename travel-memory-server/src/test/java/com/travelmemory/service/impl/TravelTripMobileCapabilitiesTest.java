package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelmemory.entity.TravelTrip;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.MemoryCompanionMapper;
import com.travelmemory.mapper.MemoryPhotoMapper;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.mapper.TravelTripMapper;
import com.travelmemory.mapper.TripCompanionMapper;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.service.ProtectedUploadReferenceService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TravelTripMobileCapabilitiesTest {

    @Test
    void createsTripWithValidatedOwnedCoverAndServerManagedFavorite() {
        TravelTripMapper mapper = mock(TravelTripMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        ProtectedUploadReferenceService uploads = mock(ProtectedUploadReferenceService.class);
        AtomicReference<TravelTrip> stored = new AtomicReference<>();
        when(currentUser.requireId()).thenReturn(7L);
        when(uploads.requireOwnedImage("/uploads/users/7/cover.jpg"))
                .thenReturn("/uploads/users/7/cover.jpg");
        when(mapper.insert(any(TravelTrip.class))).thenAnswer(invocation -> {
            TravelTrip value = invocation.getArgument(0);
            value.setId(9L);
            stored.set(value);
            return 1;
        });
        when(mapper.selectById(9L)).thenAnswer(ignored -> stored.get());
        TravelTrip request = new TravelTrip();
        request.setTitle("天津之旅");
        request.setCoverPhotoUrl("/uploads/users/7/cover.jpg");
        request.setIsFavorite(true);

        TravelTrip result = service(mapper, currentUser, uploads).create(request);

        assertEquals(7L, result.getUserId());
        assertEquals("/uploads/users/7/cover.jpg", result.getCoverPhotoUrl());
        assertEquals(false, result.getIsFavorite());
        verify(uploads).requireOwnedImage("/uploads/users/7/cover.jpg");
    }

    @Test
    void refusesForeignCoverBeforeTripIsInserted() {
        TravelTripMapper mapper = mock(TravelTripMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        ProtectedUploadReferenceService uploads = mock(ProtectedUploadReferenceService.class);
        when(currentUser.requireId()).thenReturn(7L);
        when(uploads.requireOwnedImage("/uploads/users/8/private.jpg"))
                .thenThrow(new BusinessException(400, "Invalid upload reference"));
        TravelTrip request = new TravelTrip();
        request.setTitle("天津之旅");
        request.setCoverPhotoUrl("/uploads/users/8/private.jpg");

        assertThrows(BusinessException.class, () -> service(mapper, currentUser, uploads).create(request));
        verify(mapper, never()).insert(any(TravelTrip.class));
    }

    @Test
    void favoriteToggleKeepsTripOwnershipCheck() {
        TravelTripMapper mapper = mock(TravelTripMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        TravelTrip trip = new TravelTrip();
        trip.setId(9L);
        trip.setUserId(7L);
        trip.setTitle("天津之旅");
        when(currentUser.requireId()).thenReturn(7L);
        when(mapper.selectById(9L)).thenReturn(trip);

        TravelTrip result = service(mapper, currentUser,
                mock(ProtectedUploadReferenceService.class)).setFavorite(9L, true);

        assertTrue(result.getIsFavorite());
        verify(mapper).updateById(trip);
    }

    private TravelTripServiceImpl service(TravelTripMapper mapper, CurrentUser currentUser,
            ProtectedUploadReferenceService uploads) {
        return new TravelTripServiceImpl(mapper, mock(TravelMemoryMapper.class),
                mock(MemoryPhotoMapper.class), mock(TripCompanionMapper.class),
                mock(MemoryCompanionMapper.class), currentUser, uploads);
    }
}
