package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelmemory.dto.TripDraftRequest;
import com.travelmemory.entity.TripDraft;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.TripDraftMapper;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.service.ProtectedUploadReferenceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TripDraftServiceImplTest {

    @Test
    void savesOneDraftForCurrentUserAndValidatesItsCover() {
        TripDraftMapper mapper = mock(TripDraftMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        ProtectedUploadReferenceService uploads = mock(ProtectedUploadReferenceService.class);
        when(currentUser.requireId()).thenReturn(7L);
        when(mapper.selectOne(any())).thenReturn(null);
        when(uploads.requireOwnedImage("/uploads/users/7/2026/07/cover.jpg"))
                .thenReturn("/uploads/users/7/2026/07/cover.jpg");
        TripDraftRequest request = request("  天津之旅  ", "/uploads/users/7/2026/07/cover.jpg");

        var result = new TripDraftServiceImpl(mapper, currentUser, uploads).save(request);

        assertEquals("天津之旅", result.title());
        assertEquals("/uploads/users/7/2026/07/cover.jpg", result.coverPhotoUrl());
        assertEquals(LocalDate.of(2026, 7, 14), result.startDate());
        assertEquals(new BigDecimal("39.0851000"), result.destinationLatitude());
        assertEquals(new BigDecimal("117.1994000"), result.destinationLongitude());
        verify(mapper).insert(any(TripDraft.class));
        verify(uploads).requireOwnedImage("/uploads/users/7/2026/07/cover.jpg");
    }

    @Test
    void refusesAnUnownedDraftCoverWithoutWritingDraft() {
        TripDraftMapper mapper = mock(TripDraftMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        ProtectedUploadReferenceService uploads = mock(ProtectedUploadReferenceService.class);
        when(currentUser.requireId()).thenReturn(7L);
        when(mapper.selectOne(any())).thenReturn(null);
        when(uploads.requireOwnedImage("/uploads/users/8/private.jpg"))
                .thenThrow(new BusinessException(400, "Invalid upload reference"));

        assertThrows(BusinessException.class, () -> new TripDraftServiceImpl(mapper, currentUser, uploads)
                .save(request(null, "/uploads/users/8/private.jpg")));
        verify(mapper, never()).insert(any(TripDraft.class));
        verify(mapper, never()).updateById(any(TripDraft.class));
    }

    @Test
    void returnsNullWhenCurrentUserHasNoDraft() {
        TripDraftMapper mapper = mock(TripDraftMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.requireId()).thenReturn(7L);
        when(mapper.selectOne(any())).thenReturn(null);

        assertNull(new TripDraftServiceImpl(mapper, currentUser,
                mock(ProtectedUploadReferenceService.class)).get());
    }

    private TripDraftRequest request(String title, String coverUrl) {
        TripDraftRequest request = new TripDraftRequest();
        request.setTitle(title);
        request.setDestination("天津");
        request.setDestinationLatitude(new BigDecimal("39.0851000"));
        request.setDestinationLongitude(new BigDecimal("117.1994000"));
        request.setStartDate(LocalDate.of(2026, 7, 14));
        request.setEndDate(LocalDate.of(2026, 7, 21));
        request.setDescription("海河边散步");
        request.setNotes("记得坐船");
        request.setCoverPhotoUrl(coverUrl);
        return request;
    }
}
