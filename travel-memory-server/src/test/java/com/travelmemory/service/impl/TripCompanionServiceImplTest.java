package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelmemory.entity.MemoryCompanion;
import com.travelmemory.entity.TripCompanion;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.MemoryCompanionMapper;
import com.travelmemory.mapper.TripCompanionMapper;
import com.travelmemory.service.ProtectedUploadReferenceService;
import com.travelmemory.service.TravelTripService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TripCompanionServiceImplTest {

    @Test
    void anotherUsersTripCannotBeReadOrChanged() {
        TravelTripService trips = mock(TravelTripService.class);
        TripCompanionMapper companions = mock(TripCompanionMapper.class);
        when(trips.getById(20L)).thenThrow(new BusinessException(404, "Trip not found"));
        TripCompanionServiceImpl service = service(companions, mock(MemoryCompanionMapper.class), trips);

        BusinessException listError = assertThrows(BusinessException.class, () -> service.list(20L));
        BusinessException createError = assertThrows(BusinessException.class, () -> service.create(20L, "小雨"));

        assertEquals(404, listError.getCode());
        assertEquals(404, createError.getCode());
        verify(companions, never()).selectList(any());
        verify(companions, never()).insert(any(TripCompanion.class));
    }

    @Test
    void memoryCannotUseACompanionFromAnotherTrip() {
        TravelTripService trips = mock(TravelTripService.class);
        TripCompanionMapper companions = mock(TripCompanionMapper.class);
        MemoryCompanionMapper relations = mock(MemoryCompanionMapper.class);
        when(companions.selectList(any())).thenReturn(List.of(companion(8L, 2L, "阿杰", true)));
        TripCompanionServiceImpl service = service(companions, relations, trips);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.replaceMemoryCompanions(10L, 1L, List.of(8L)));

        assertEquals(404, error.getCode());
        verify(relations, never()).delete(any());
        verify(relations, never()).insert(any(MemoryCompanion.class));
    }

    @Test
    void duplicateIdsAreStoredOnlyOnce() {
        TravelTripService trips = mock(TravelTripService.class);
        TripCompanionMapper companions = mock(TripCompanionMapper.class);
        MemoryCompanionMapper relations = mock(MemoryCompanionMapper.class);
        when(companions.selectList(any())).thenReturn(List.of(companion(3L, 1L, "小雨", true)));
        TripCompanionServiceImpl service = service(companions, relations, trips);

        service.replaceMemoryCompanions(10L, 1L, List.of(3L, 3L));

        verify(relations).delete(any());
        verify(relations, times(1)).insert(any(MemoryCompanion.class));
    }

    @Test
    void batchAssemblyQueriesRelationsAndCompanionsOnce() {
        TravelTripService trips = mock(TravelTripService.class);
        TripCompanionMapper companions = mock(TripCompanionMapper.class);
        MemoryCompanionMapper relations = mock(MemoryCompanionMapper.class);
        MemoryCompanion first = relation(10L, 3L);
        MemoryCompanion second = relation(11L, 4L);
        when(relations.selectList(any())).thenReturn(List.of(first, second));
        when(companions.selectList(any())).thenReturn(List.of(
                companion(3L, 1L, "小雨", true),
                companion(4L, 1L, "阿杰", false)));
        TripCompanionServiceImpl service = service(companions, relations, trips);

        Map<Long, List<com.travelmemory.dto.CompanionSummary>> result = service.findByMemoryIds(Set.of(10L, 11L));

        assertEquals("小雨", result.get(10L).get(0).getName());
        assertEquals("阿杰", result.get(11L).get(0).getName());
        verify(relations, times(1)).selectList(any());
        verify(companions, times(1)).selectList(any());
    }

    @Test
    void deactivationDoesNotDeleteHistoricalMemoryRelations() {
        TravelTripService trips = mock(TravelTripService.class);
        TripCompanionMapper companions = mock(TripCompanionMapper.class);
        MemoryCompanionMapper relations = mock(MemoryCompanionMapper.class);
        TripCompanion companion = companion(3L, 1L, "小雨", true);
        when(companions.selectById(3L)).thenReturn(companion);
        TripCompanionServiceImpl service = service(companions, relations, trips);

        TripCompanion updated = service.setActive(1L, 3L, false);

        assertEquals(false, updated.getActive());
        verify(companions).updateById(companion);
        verify(relations, never()).delete(any());
    }

    private TripCompanionServiceImpl service(TripCompanionMapper companions,
            MemoryCompanionMapper relations, TravelTripService trips) {
        return new TripCompanionServiceImpl(companions, relations, trips,
                mock(ProtectedUploadReferenceService.class));
    }

    private TripCompanion companion(Long id, Long tripId, String name, boolean active) {
        TripCompanion companion = new TripCompanion();
        companion.setId(id);
        companion.setTripId(tripId);
        companion.setName(name);
        companion.setSortOrder(id.intValue());
        companion.setActive(active);
        return companion;
    }

    private MemoryCompanion relation(Long memoryId, Long companionId) {
        MemoryCompanion relation = new MemoryCompanion();
        relation.setMemoryId(memoryId);
        relation.setCompanionId(companionId);
        return relation;
    }
}
