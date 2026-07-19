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
import java.math.BigDecimal;
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
        request.setDestination("天津市");
        request.setDestinationCountry("中国");
        request.setDestinationLatitude(new BigDecimal("39.0851000"));
        request.setDestinationLongitude(new BigDecimal("117.1994000"));
        request.setCoverPhotoUrl("/uploads/users/7/cover.jpg");
        request.setIsFavorite(true);

        TravelTrip result = service(mapper, currentUser, uploads).create(request);

        assertEquals(7L, result.getUserId());
        assertEquals("/uploads/users/7/cover.jpg", result.getCoverPhotoUrl());
        assertEquals(false, result.getIsFavorite());
        assertEquals("中国", result.getDestinationCountry());
        assertEquals(new BigDecimal("39.0851000"), result.getDestinationLatitude());
        assertEquals(new BigDecimal("117.1994000"), result.getDestinationLongitude());
        verify(uploads).requireOwnedImage("/uploads/users/7/cover.jpg");
    }

    @Test
    void refusesIncompleteDestinationCoordinates() {
        TravelTripMapper mapper = mock(TravelTripMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        TravelTrip request = new TravelTrip();
        request.setTitle("天津之旅");
        request.setDestinationLatitude(new BigDecimal("39.0851000"));

        assertThrows(BusinessException.class, () -> service(mapper, currentUser,
                mock(ProtectedUploadReferenceService.class)).create(request));
        verify(mapper, never()).insert(any(TravelTrip.class));
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
    void updatesCoverOnlyAfterOwnedUploadValidation() {
        TravelTripMapper mapper = mock(TravelTripMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        ProtectedUploadReferenceService uploads = mock(ProtectedUploadReferenceService.class);
        AtomicReference<TravelTrip> stored = new AtomicReference<>();
        TravelTrip existing = new TravelTrip();
        existing.setId(9L);
        existing.setUserId(7L);
        existing.setTitle("天津之旅");
        existing.setCoverPhotoUrl("/uploads/users/7/old.jpg");
        stored.set(existing);
        when(currentUser.requireId()).thenReturn(7L);
        when(mapper.selectById(9L)).thenAnswer(ignored -> stored.get());
        when(uploads.requireOwnedImage("/uploads/users/7/new.jpg"))
                .thenReturn("/uploads/users/7/new.jpg");
        when(mapper.updateById(any(TravelTrip.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        TravelTrip request = new TravelTrip();
        request.setTitle("天津之旅");
        request.setCoverPhotoUrl("/uploads/users/7/new.jpg");

        TravelTrip result = service(mapper, currentUser, uploads).update(9L, request);

        assertEquals("/uploads/users/7/new.jpg", result.getCoverPhotoUrl());
        verify(uploads).requireOwnedImage("/uploads/users/7/new.jpg");
    }

    @Test
    void refusesForeignCoverDuringTripUpdate() {
        TravelTripMapper mapper = mock(TravelTripMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        ProtectedUploadReferenceService uploads = mock(ProtectedUploadReferenceService.class);
        TravelTrip existing = new TravelTrip();
        existing.setId(9L);
        existing.setUserId(7L);
        existing.setTitle("天津之旅");
        existing.setCoverPhotoUrl("/uploads/users/7/old.jpg");
        when(currentUser.requireId()).thenReturn(7L);
        when(mapper.selectById(9L)).thenReturn(existing);
        when(uploads.requireOwnedImage("/uploads/users/8/private.jpg"))
                .thenThrow(new BusinessException(400, "Invalid upload reference"));
        TravelTrip request = new TravelTrip();
        request.setTitle("天津之旅");
        request.setCoverPhotoUrl("/uploads/users/8/private.jpg");

        assertThrows(BusinessException.class, () -> service(mapper, currentUser, uploads).update(9L, request));

        verify(mapper, never()).updateById(any(TravelTrip.class));
    }

    @Test
    void clearsExplicitCoverOnlyWhenUpdateRequestsIt() {
        TravelTripMapper mapper = mock(TravelTripMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        AtomicReference<TravelTrip> stored = new AtomicReference<>();
        TravelTrip existing = new TravelTrip();
        existing.setId(9L);
        existing.setUserId(7L);
        existing.setTitle("天津之旅");
        existing.setCoverPhotoUrl("/uploads/users/7/old.jpg");
        stored.set(existing);
        when(currentUser.requireId()).thenReturn(7L);
        when(mapper.selectById(9L)).thenAnswer(ignored -> stored.get());
        when(mapper.updateById(any(TravelTrip.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        TravelTrip request = new TravelTrip();
        request.setTitle("天津之旅");

        TravelTrip result = service(mapper, currentUser,
                mock(ProtectedUploadReferenceService.class)).update(9L, request, true);

        assertEquals(null, result.getCoverPhotoUrl());
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
                mock(MemoryCompanionMapper.class), currentUser, uploads,
                mock(com.travelmemory.service.OrphanUploadCleanupService.class));
    }
}
