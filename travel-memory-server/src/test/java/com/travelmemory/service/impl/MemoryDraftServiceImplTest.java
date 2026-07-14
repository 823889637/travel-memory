package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelmemory.dto.MemoryDraftRequest;
import com.travelmemory.dto.MemoryDraftResponse;
import com.travelmemory.entity.MemoryDraft;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.entity.TravelTrip;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.MemoryDraftMapper;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.service.TripCompanionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class MemoryDraftServiceImplTest {

    @TempDir
    Path uploadRoot;

    private MemoryDraftMapper memoryDraftMapper;
    private TravelMemoryMapper travelMemoryMapper;
    private TravelTripService travelTripService;
    private TripCompanionService tripCompanionService;
    private CurrentUser currentUser;
    private MemoryDraftServiceImpl service;

    @BeforeEach
    void setUp() {
        memoryDraftMapper = mock(MemoryDraftMapper.class);
        travelMemoryMapper = mock(TravelMemoryMapper.class);
        travelTripService = mock(TravelTripService.class);
        tripCompanionService = mock(TripCompanionService.class);
        currentUser = mock(CurrentUser.class);
        service = new MemoryDraftServiceImpl(
                memoryDraftMapper,
                travelMemoryMapper,
                travelTripService,
                tripCompanionService,
                currentUser);
        ReflectionTestUtils.setField(service, "uploadDir", uploadRoot.toString());
        when(currentUser.requireId()).thenReturn(7L);
        when(travelTripService.getById(11L)).thenReturn(trip(11L));
        when(tripCompanionService.list(11L)).thenReturn(List.of());
        when(memoryDraftMapper.selectOne(any())).thenReturn(null);
        when(memoryDraftMapper.insert(any(MemoryDraft.class))).thenAnswer(invocation -> {
            MemoryDraft draft = invocation.getArgument(0);
            draft.setId(31L);
            return 1;
        });
    }

    @Test
    void savesAndReturnsControlledPhotoReferences() throws IOException {
        Path photo = uploadRoot.resolve("users/7/2026/07/draft.jpg");
        Files.createDirectories(photo.getParent());
        Files.writeString(photo, "draft");
        MemoryDraftRequest request = request(List.of("/uploads/users/7/2026/07/draft.jpg"));

        MemoryDraftResponse response = service.save(request);

        assertEquals(31L, response.getId());
        assertEquals(11L, response.getTripId());
        assertIterableEquals(request.getPhotoUrls(), response.getPhotoUrls());
        assertNotNull(response.getUpdateTime());
        verify(memoryDraftMapper).insert(any(MemoryDraft.class));
    }

    @Test
    void rejectsPhotoOutsideCurrentUsersUploadDirectory() throws IOException {
        Path photo = uploadRoot.resolve("users/8/2026/07/other.jpg");
        Files.createDirectories(photo.getParent());
        Files.writeString(photo, "other");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.save(request(List.of("/uploads/users/8/2026/07/other.jpg"))));

        assertEquals(404, exception.getCode());
    }

    @Test
    void rejectsMissingUploadedPhoto() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.save(request(List.of("/uploads/users/7/2026/07/missing.jpg"))));

        assertEquals(400, exception.getCode());
    }

    @Test
    void rejectsEditingMemoryFromAnotherTrip() {
        TravelMemory memory = new TravelMemory();
        memory.setId(21L);
        memory.setTripId(12L);
        when(travelMemoryMapper.selectById(21L)).thenReturn(memory);
        MemoryDraftRequest request = request(List.of());
        request.setMemoryId(21L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.save(request));

        assertEquals(404, exception.getCode());
    }

    private MemoryDraftRequest request(List<String> photoUrls) {
        MemoryDraftRequest request = new MemoryDraftRequest();
        request.setTripId(11L);
        request.setContent("  evening by the river  ");
        request.setRecordTime(LocalDateTime.of(2026, 7, 14, 20, 30));
        request.setPhotoUrls(photoUrls);
        request.setCompanionIds(List.of());
        return request;
    }

    private TravelTrip trip(Long id) {
        TravelTrip trip = new TravelTrip();
        trip.setId(id);
        return trip;
    }
}
