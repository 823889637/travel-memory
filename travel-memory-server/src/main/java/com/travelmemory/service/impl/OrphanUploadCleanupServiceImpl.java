package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.travelmemory.config.UploadCleanupProperties;
import com.travelmemory.dto.CleanupResult;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.entity.MemoryPhoto;
import com.travelmemory.entity.TravelTrip;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.mapper.MemoryPhotoMapper;
import com.travelmemory.mapper.MemoryDraftMapper;
import com.travelmemory.mapper.TravelTripMapper;
import com.travelmemory.mapper.AppUserMapper;
import com.travelmemory.mapper.TripCompanionMapper;
import com.travelmemory.mapper.TripDraftMapper;
import com.travelmemory.service.OrphanUploadCleanupService;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OrphanUploadCleanupServiceImpl implements OrphanUploadCleanupService {

    private static final Logger log = LoggerFactory.getLogger(OrphanUploadCleanupServiceImpl.class);

    private final TravelMemoryMapper travelMemoryMapper;
    private final TravelTripMapper travelTripMapper;
    private final MemoryPhotoMapper memoryPhotoMapper;
    private final MemoryDraftMapper memoryDraftMapper;
    private final UploadCleanupProperties cleanupProperties;
    private final AppUserMapper appUserMapper;
    private final TripCompanionMapper tripCompanionMapper;
    private final TripDraftMapper tripDraftMapper;

    @Value("${app.upload.dir:../uploads}")
    private String uploadDir;

    @Autowired
    public OrphanUploadCleanupServiceImpl(
            TravelMemoryMapper travelMemoryMapper,
            TravelTripMapper travelTripMapper,
            MemoryPhotoMapper memoryPhotoMapper,
            MemoryDraftMapper memoryDraftMapper,
            AppUserMapper appUserMapper,
            TripCompanionMapper tripCompanionMapper,
            TripDraftMapper tripDraftMapper,
            UploadCleanupProperties cleanupProperties
    ) {
        this.travelMemoryMapper = travelMemoryMapper;
        this.travelTripMapper = travelTripMapper;
        this.memoryPhotoMapper = memoryPhotoMapper;
        this.memoryDraftMapper = memoryDraftMapper;
        this.appUserMapper = appUserMapper;
        this.tripCompanionMapper = tripCompanionMapper;
        this.tripDraftMapper = tripDraftMapper;
        this.cleanupProperties = cleanupProperties;
    }

    @Override
    public CleanupResult cleanupOrphans() {
        Instant startedAt = Instant.now();
        CleanupResult result = new CleanupResult();
        result.setDryRun(cleanupProperties.isDryRun());

        if (!cleanupProperties.isEnabled()) {
            return finish(result, startedAt, "Upload cleanup is disabled");
        }

        if (cleanupProperties.getRetentionHours() <= 0) {
            return finish(result, startedAt, "Invalid retention hours; cleanup skipped");
        }

        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(uploadRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(uploadRoot)) {
            return finish(result, startedAt, "Upload directory is unavailable; cleanup skipped");
        }

        Set<Path> referencedPaths;
        try {
            referencedPaths = findReferencedPaths(uploadRoot);
        } catch (RuntimeException exception) {
            result.setFailedCount(1);
            log.warn("Upload cleanup skipped because database reference collection failed", exception);
            return finish(result, startedAt, "Database reference collection failed; cleanup skipped");
        }

        Instant deleteBefore = startedAt.minus(Duration.ofHours(cleanupProperties.getRetentionHours()));
        try (Stream<Path> paths = Files.walk(uploadRoot)) {
            paths.forEach(path -> processPath(path, uploadRoot, referencedPaths, deleteBefore, result));
        } catch (IOException | RuntimeException exception) {
            result.setFailedCount(result.getFailedCount() + 1);
            log.warn("Upload cleanup scan failed", exception);
        }

        String message = cleanupProperties.isDryRun()
                ? "DRY-RUN completed"
                : "Upload cleanup completed";
        return finish(result, startedAt, message);
    }

    private Set<Path> findReferencedPaths(Path uploadRoot) {
        Set<Path> referencedPaths = new HashSet<>();
        addReferencedUrls(referencedPaths, uploadRoot, travelMemoryMapper.selectObjs(
                new QueryWrapper<TravelMemory>()
                        .select("photo_url")
                        .isNotNull("photo_url")));
        addReferencedUrls(referencedPaths, uploadRoot, travelTripMapper.selectObjs(
                new QueryWrapper<TravelTrip>()
                        .select("cover_photo_url")
                        .isNotNull("cover_photo_url")));
        addReferencedUrls(referencedPaths, uploadRoot, memoryPhotoMapper.selectObjs(
                new QueryWrapper<MemoryPhoto>().select("photo_url").isNotNull("photo_url")));
        addReferencedDraftUrls(referencedPaths, uploadRoot, memoryDraftMapper.selectObjs(
                new QueryWrapper<com.travelmemory.entity.MemoryDraft>()
                        .select("photo_urls")
                        .isNotNull("photo_urls")));
        addReferencedUrls(referencedPaths, uploadRoot, appUserMapper.selectObjs(
                new QueryWrapper<com.travelmemory.entity.AppUser>()
                        .select("avatar_url")
                        .isNotNull("avatar_url")));
        addReferencedUrls(referencedPaths, uploadRoot, tripCompanionMapper.selectObjs(
                new QueryWrapper<com.travelmemory.entity.TripCompanion>()
                        .select("avatar_url")
                        .isNotNull("avatar_url")));
        addReferencedUrls(referencedPaths, uploadRoot, tripDraftMapper.selectObjs(
                new QueryWrapper<com.travelmemory.entity.TripDraft>()
                        .select("cover_photo_url")
                        .isNotNull("cover_photo_url")));
        return referencedPaths;
    }

    private void addReferencedDraftUrls(Set<Path> referencedPaths, Path uploadRoot, List<Object> values) {
        for (Object value : values) {
            if (value == null) continue;
            for (String url : value.toString().split("\\R")) {
                Path relativePath = normalizeReferencedUrl(url, uploadRoot);
                if (relativePath != null) referencedPaths.add(relativePath);
            }
        }
    }

    private void addReferencedUrls(Set<Path> referencedPaths, Path uploadRoot, List<Object> urls) {
        for (Object value : urls) {
            Path relativePath = normalizeReferencedUrl(value == null ? null : value.toString(), uploadRoot);
            if (relativePath != null) {
                referencedPaths.add(relativePath);
            }
        }
    }

    private Path normalizeReferencedUrl(String value, Path uploadRoot) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String normalized = decode(value.trim()).replace('\\', '/');
        String pathPart = extractUploadsPath(normalized);
        if (pathPart == null) {
            return null;
        }

        while (pathPart.startsWith("/")) {
            pathPart = pathPart.substring(1);
        }
        if (pathPart.startsWith("uploads/")) {
            pathPart = pathPart.substring("uploads/".length());
        }
        if (pathPart.isBlank() || pathPart.contains(":") || pathPart.startsWith("..")) {
            return null;
        }

        Path candidate;
        try {
            candidate = Paths.get(pathPart).normalize();
        } catch (RuntimeException exception) {
            return null;
        }
        if (candidate.isAbsolute() || candidate.startsWith("..")) {
            return null;
        }

        Path resolved = uploadRoot.resolve(candidate).normalize();
        if (!resolved.startsWith(uploadRoot) || resolved.equals(uploadRoot)) {
            return null;
        }
        return uploadRoot.relativize(resolved);
    }

    private String extractUploadsPath(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.isAbsolute()) {
                // The current application stores relative /uploads URLs. Without a configured trusted host,
                // treating an arbitrary absolute URL as local would incorrectly protect a local file.
                return null;
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        int queryIndex = value.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? value.substring(0, queryIndex) : value;
        int fragmentIndex = withoutQuery.indexOf('#');
        return fragmentIndex >= 0 ? withoutQuery.substring(0, fragmentIndex) : withoutQuery;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private void processPath(
            Path path,
            Path uploadRoot,
            Set<Path> referencedPaths,
            Instant deleteBefore,
            CleanupResult result
    ) {
        if (path.equals(uploadRoot) || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            return;
        }

        Path candidate = path.toAbsolutePath().normalize();
        if (!candidate.startsWith(uploadRoot) || candidate.equals(uploadRoot)) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            return;
        }

        Path relativePath = uploadRoot.relativize(candidate);
        if (isTemporaryUpload(relativePath)) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            return;
        }
        result.setScannedCount(result.getScannedCount() + 1);
        if (referencedPaths.contains(relativePath)) {
            result.setReferencedCount(result.getReferencedCount() + 1);
            return;
        }

        FileTime lastModifiedTime;
        try {
            lastModifiedTime = Files.getLastModifiedTime(candidate, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            result.setFailedCount(result.getFailedCount() + 1);
            log.debug("Unable to read upload file time: {}", relativePath, exception);
            return;
        }

        if (!lastModifiedTime.toInstant().isBefore(deleteBefore)) {
            result.setRecentCount(result.getRecentCount() + 1);
            return;
        }

        long size;
        try {
            size = Files.size(candidate);
        } catch (IOException exception) {
            result.setFailedCount(result.getFailedCount() + 1);
            log.debug("Unable to read upload file size: {}", relativePath, exception);
            return;
        }

        result.setCandidateCount(result.getCandidateCount() + 1);
        result.setCandidateBytes(result.getCandidateBytes() + size);
        if (cleanupProperties.isDryRun()) {
            log.debug("DRY-RUN orphan upload candidate: {}", relativePath);
            return;
        }

        try {
            if (Files.isSymbolicLink(candidate) || !candidate.startsWith(uploadRoot)) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                return;
            }
            if (Files.deleteIfExists(candidate)) {
                result.setDeletedCount(result.getDeletedCount() + 1);
                result.setDeletedBytes(result.getDeletedBytes() + size);
            }
        } catch (IOException exception) {
            result.setFailedCount(result.getFailedCount() + 1);
            log.warn("Failed to delete orphan upload file: {}", relativePath, exception);
        }
    }

    private boolean isTemporaryUpload(Path relativePath) {
        String filename = relativePath.getFileName().toString().toLowerCase(Locale.ROOT);
        return filename.endsWith(".tmp")
                || filename.endsWith(".part")
                || filename.endsWith(".upload")
                || filename.endsWith(".uploading")
                || filename.endsWith(".crdownload");
    }

    private CleanupResult finish(CleanupResult result, Instant startedAt, String message) {
        result.setDurationMillis(Duration.between(startedAt, Instant.now()).toMillis());
        result.setMessage(message);
        if (result.getFailedCount() > 0) {
            log.warn("Upload cleanup finished: dryRun={}, scanned={}, referenced={}, candidates={}, deleted={}, failed={}, candidateBytes={}, durationMillis={}",
                    result.isDryRun(), result.getScannedCount(), result.getReferencedCount(), result.getCandidateCount(),
                    result.getDeletedCount(), result.getFailedCount(), result.getCandidateBytes(), result.getDurationMillis());
        } else {
            log.info("Upload cleanup finished: dryRun={}, scanned={}, referenced={}, candidates={}, deleted={}, failed={}, candidateBytes={}, durationMillis={}",
                    result.isDryRun(), result.getScannedCount(), result.getReferencedCount(), result.getCandidateCount(),
                    result.getDeletedCount(), result.getFailedCount(), result.getCandidateBytes(), result.getDurationMillis());
        }
        return result;
    }
}
