package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelmemory.entity.MemoryPhoto;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.MemoryPhotoMapper;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.mapper.TravelTripMapper;
import com.travelmemory.service.FileStorageService;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.util.ImageMetadataExtractor;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class TravelMemoryMultiPhotoServiceTest {

    @TempDir
    Path uploadDir;

    @Test
    void fallsBackToLegacySinglePhotoWhenPhotoRowsDoNotExist() {
        Fixture fixture = fixture();
        TravelMemory memory = memory(10L, "/uploads/legacy.jpg");
        when(fixture.memoryMapper.selectById(10L)).thenReturn(memory);

        TravelMemory result = fixture.service.getById(10L);

        assertEquals(1, result.getPhotoCount());
        assertEquals("/uploads/legacy.jpg", result.getPhotos().get(0).getPhotoUrl());
    }

    @Test
    void createsThreePhotosAndKeepsTheFirstAsPrimary() throws Exception {
        Fixture fixture = fixture();
        uploadedFile("one.jpg"); uploadedFile("two.jpg"); uploadedFile("three.jpg");
        TravelMemory memory = memory(null, null);
        memory.setTripId(1L);
        memory.setRecordTime(LocalDateTime.of(2026, 7, 13, 10, 0));
        memory.setPhotos(List.of(photo(null, "/uploads/one.jpg", 0), photo(null, "/uploads/two.jpg", 1), photo(null, "/uploads/three.jpg", 2)));
        when(fixture.memoryMapper.insert(org.mockito.ArgumentMatchers.<TravelMemory>any())).thenAnswer(invocation -> { invocation.<TravelMemory>getArgument(0).setId(10L); return 1; });
        when(fixture.memoryMapper.selectById(10L)).thenReturn(memory);

        TravelMemory result = fixture.service.create(memory, List.of());

        assertEquals("/uploads/one.jpg", result.getPhotoUrl());
        assertEquals(3, result.getPhotoCount());
        assertEquals(List.of("/uploads/one.jpg", "/uploads/two.jpg", "/uploads/three.jpg"),
                result.getPhotos().stream().map(MemoryPhoto::getPhotoUrl).toList());
    }

    @Test
    void rejectsMoreThanSixPhotosBeforeWritingMemory() {
        Fixture fixture = fixture();
        TravelMemory memory = memory(null, null);
        memory.setTripId(1L);
        memory.setPhotos(List.of(
                photo(null, "/uploads/1.jpg", 0), photo(null, "/uploads/2.jpg", 1), photo(null, "/uploads/3.jpg", 2),
                photo(null, "/uploads/4.jpg", 3), photo(null, "/uploads/5.jpg", 4), photo(null, "/uploads/6.jpg", 5),
                photo(null, "/uploads/7.jpg", 6)));

        assertThrows(BusinessException.class, () -> fixture.service.create(memory, List.of()));
        verify(fixture.memoryMapper, never()).insert(org.mockito.ArgumentMatchers.<TravelMemory>any());
    }

    @Test
    void rejectsExternalTraversalAndMissingPhotoUrls() {
        Fixture fixture = fixture();
        assertCreateRejected(fixture, "https://example.test/photo.jpg");
        assertCreateRejected(fixture, "/uploads/../outside.jpg");
        assertCreateRejected(fixture, "/uploads/missing.jpg");
    }

    @Test
    void rejectsAnotherUsersUploadedPhotoUrl() {
        Fixture fixture = fixture();

        assertCreateRejected(fixture, "/uploads/users/2/2026/07/private.jpg");
    }

    @Test
    void rejectsDuplicatePhotoUrlsAfterNormalization() throws Exception {
        Fixture fixture = fixture();
        uploadedFile("duplicate.jpg");
        TravelMemory memory = memory(null, null); memory.setTripId(1L);
        memory.setPhotos(List.of(photo(null, "/uploads/duplicate.jpg", 0), photo(null, "/uploads/%64uplicate.jpg", 1)));
        assertThrows(BusinessException.class, () -> fixture.service.create(memory, List.of()));
    }

    @Test
    void reorderingSynchronizesPrimaryPhotoUrl() {
        Fixture fixture = fixture();
        TravelMemory memory = memory(10L, "/uploads/one.jpg");
        when(fixture.memoryMapper.selectById(10L)).thenReturn(memory);
        fixture.photos.addAll(List.of(photo(1L, "/uploads/one.jpg", 0), photo(2L, "/uploads/two.jpg", 1), photo(3L, "/uploads/three.jpg", 2)));

        TravelMemory result = fixture.service.reorderPhotos(10L, List.of(2L, 1L, 3L));

        assertEquals("/uploads/two.jpg", result.getPhotoUrl());
        assertEquals(2L, result.getPhotos().get(0).getId());
        verify(fixture.tripService).replaceCoverIfMatches(1L, "/uploads/one.jpg", "/uploads/two.jpg");
    }

    @Test
    void deletingPrimaryPromotesTheNextPhoto() {
        Fixture fixture = fixture();
        TravelMemory memory = memory(10L, "/uploads/one.jpg");
        when(fixture.memoryMapper.selectById(10L)).thenReturn(memory);
        fixture.photos.addAll(List.of(photo(1L, "/uploads/one.jpg", 0), photo(2L, "/uploads/two.jpg", 1)));
        when(fixture.photoMapper.deleteById(1L)).thenAnswer(invocation -> { fixture.photos.removeIf(photo -> photo.getId().equals(1L)); return 1; });

        TravelMemory result = fixture.service.deletePhoto(10L, 1L);

        assertEquals("/uploads/two.jpg", result.getPhotoUrl());
        assertEquals(1, result.getPhotoCount());
    }

    @Test
    void timelineLoadsAllPhotoRowsWithOneBatchQuery() {
        Fixture fixture = fixture();
        when(fixture.memoryMapper.selectList(any())).thenReturn(List.of(memory(10L, "/uploads/one.jpg"), memory(11L, "/uploads/two.jpg")));
        fixture.photos.addAll(List.of(photo(1L, "/uploads/one.jpg", 0), photo(2L, "/uploads/two.jpg", 0)));

        List<TravelMemory> result = fixture.service.timeline(1L);

        assertEquals(2, result.size());
        verify(fixture.photoMapper, times(1)).selectList(any());
    }

    @Test
    void deletingMemoryRemovesPhotoRows() {
        Fixture fixture = fixture();
        TravelMemory memory = memory(10L, "/uploads/one.jpg");
        when(fixture.memoryMapper.selectById(10L)).thenReturn(memory);

        fixture.service.delete(10L);

        verify(fixture.photoMapper).delete(any());
        verify(fixture.memoryMapper).deleteById(10L);
    }

    @Test
    void deletingTripRemovesMemoryPhotoRowsBeforeMemories() {
        TravelTripMapper tripMapper = mock(TravelTripMapper.class);
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        MemoryPhotoMapper photoMapper = mock(MemoryPhotoMapper.class);
        var trip = new com.travelmemory.entity.TravelTrip(); trip.setId(1L); trip.setUserId(1L);
        when(tripMapper.selectById(1L)).thenReturn(trip);
        when(memoryMapper.selectList(any())).thenReturn(List.of(memory(10L, "/uploads/one.jpg")));
        TravelTripServiceImpl service = new TravelTripServiceImpl(tripMapper, memoryMapper, photoMapper, currentUser());

        service.delete(1L);

        verify(photoMapper).delete(any());
        verify(memoryMapper).delete(any());
        verify(tripMapper).deleteById(1L);
    }

    private Fixture fixture() {
        TravelMemoryMapper memoryMapper = mock(TravelMemoryMapper.class);
        MemoryPhotoMapper photoMapper = mock(MemoryPhotoMapper.class);
        TravelTripService tripService = mock(TravelTripService.class);
        List<MemoryPhoto> photos = new ArrayList<>();
        AtomicLong ids = new AtomicLong(1);
        when(photoMapper.selectList(any())).thenAnswer(invocation -> photos.stream()
                .sorted(Comparator.comparing(MemoryPhoto::getSortOrder).thenComparing(MemoryPhoto::getId)).toList());
        when(photoMapper.insert(org.mockito.ArgumentMatchers.<MemoryPhoto>any())).thenAnswer(invocation -> { MemoryPhoto photo = invocation.getArgument(0); photo.setId(ids.getAndIncrement()); photos.add(photo); return 1; });
        when(photoMapper.updateById(org.mockito.ArgumentMatchers.<MemoryPhoto>any())).thenReturn(1);
        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(memoryMapper, photoMapper, tripService,
                mock(FileStorageService.class), mock(ImageMetadataExtractor.class), currentUser());
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
        return new Fixture(service, memoryMapper, photoMapper, tripService, photos);
    }

    private void assertCreateRejected(Fixture fixture, String url) {
        TravelMemory memory = memory(null, null); memory.setTripId(1L); memory.setPhotos(List.of(photo(null, url, 0)));
        assertThrows(BusinessException.class, () -> fixture.service.create(memory, List.of()));
    }

    private void uploadedFile(String name) throws Exception { Files.writeString(uploadDir.resolve(name), "photo"); }

    private TravelMemory memory(Long id, String photoUrl) {
        TravelMemory memory = new TravelMemory(); memory.setId(id); memory.setTripId(1L); memory.setPhotoUrl(photoUrl); memory.setRecordTime(LocalDateTime.of(2026, 7, 13, 10, 0)); return memory;
    }
    private MemoryPhoto photo(Long id, String url, int order) {
        MemoryPhoto photo = new MemoryPhoto(); photo.setId(id); photo.setMemoryId(10L); photo.setPhotoUrl(url); photo.setSortOrder(order); return photo;
    }
    private CurrentUser currentUser() {
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.requireId()).thenReturn(1L);
        return currentUser;
    }
    private record Fixture(TravelMemoryServiceImpl service, TravelMemoryMapper memoryMapper, MemoryPhotoMapper photoMapper,
                           TravelTripService tripService, List<MemoryPhoto> photos) { }
}
