package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelmemory.dto.CompanionSummary;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.mapper.MemoryPhotoMapper;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.service.FileStorageService;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.service.TripCompanionService;
import com.travelmemory.util.ImageMetadataExtractor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TravelMemoryCompanionServiceTest {

    @Test
    void createStoresCompanionsAndReturnsThem() {
        Fixture fixture = fixture();
        TravelMemory memory = memory(null, 1L);
        memory.setCompanionIds(List.of(3L, 4L, 5L));
        when(fixture.memoryMapper.insert(any(TravelMemory.class))).thenAnswer(invocation -> {
            TravelMemory inserted = invocation.getArgument(0);
            inserted.setId(10L);
            return 1;
        });
        when(fixture.memoryMapper.selectById(10L)).thenAnswer(invocation -> memory);
        when(fixture.companions.findByMemoryIds(Set.of(10L))).thenReturn(Map.of(10L, List.of(
                new CompanionSummary(3L, "小雨"),
                new CompanionSummary(4L, "阿杰"),
                new CompanionSummary(5L, "小米"))));

        TravelMemory created = fixture.service.create(memory, List.of());

        verify(fixture.companions).replaceMemoryCompanions(10L, 1L, List.of(3L, 4L, 5L));
        assertEquals(3, created.getCompanions().size());
    }

    @Test
    void oldUpdateRequestWithoutCompanionIdsPreservesRelations() {
        Fixture fixture = fixture();
        TravelMemory existing = memory(10L, 1L);
        when(fixture.memoryMapper.selectById(10L)).thenReturn(existing);
        when(fixture.companions.findByMemoryIds(Set.of(10L))).thenReturn(Map.of());
        TravelMemory update = memory(null, null);
        update.setCompanionIds(null);

        fixture.service.update(10L, update);

        verify(fixture.companions, never()).replaceMemoryCompanions(any(), any(), any());
    }

    @Test
    void timelineAssemblesAllCompanionsWithOneBatchCall() {
        Fixture fixture = fixture();
        TravelMemory first = memory(10L, 1L);
        TravelMemory second = memory(11L, 1L);
        when(fixture.memoryMapper.selectList(any())).thenReturn(List.of(first, second));
        when(fixture.companions.findByMemoryIds(Set.of(10L, 11L))).thenReturn(Map.of(
                10L, List.of(new CompanionSummary(3L, "小雨")),
                11L, List.of(new CompanionSummary(4L, "阿杰"))));

        List<TravelMemory> result = fixture.service.timeline(1L);

        assertEquals("小雨", result.get(0).getCompanions().get(0).getName());
        assertEquals("阿杰", result.get(1).getCompanions().get(0).getName());
        verify(fixture.companions, times(1)).findByMemoryIds(Set.of(10L, 11L));
    }

    private Fixture fixture() {
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        MemoryPhotoMapper photos = mock(MemoryPhotoMapper.class);
        TravelTripService trips = mock(TravelTripService.class);
        TripCompanionService companions = mock(TripCompanionService.class);
        when(photos.selectList(any())).thenReturn(List.of());
        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(memoryMapper, photos, trips,
                mock(FileStorageService.class), mock(ImageMetadataExtractor.class), mock(CurrentUser.class), companions);
        return new Fixture(service, memoryMapper, companions);
    }

    private TravelMemory memory(Long id, Long tripId) {
        TravelMemory memory = new TravelMemory();
        memory.setId(id);
        memory.setTripId(tripId);
        memory.setRecordTime(LocalDateTime.of(2026, 7, 3, 10, 0));
        return memory;
    }

    private record Fixture(TravelMemoryServiceImpl service, TravelMemoryMapper memoryMapper,
            TripCompanionService companions) {
    }
}
