package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmemory.dto.CleanupResult;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.service.OrphanUploadCleanupService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrphanUploadCleanupServiceImpl implements OrphanUploadCleanupService {

    private static final Logger log = LoggerFactory.getLogger(OrphanUploadCleanupServiceImpl.class);
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "heic", "heif");

    private final TravelMemoryMapper travelMemoryMapper;

    @Value("${app.upload.dir:../uploads}")
    private String uploadDir;

    @Value("${app.upload.cleanup.enabled:false}")
    private boolean cleanupEnabled;

    @Value("${app.upload.cleanup.orphan-retention-hours:24}")
    private long orphanRetentionHours;

    public OrphanUploadCleanupServiceImpl(TravelMemoryMapper travelMemoryMapper) {
        this.travelMemoryMapper = travelMemoryMapper;
    }

    @Override
    public CleanupResult cleanupOrphans(boolean dryRun) {
        CleanupResult result = new CleanupResult();
        result.setDryRun(dryRun);

        if (!cleanupEnabled) {
            result.setMessage("Upload orphan cleanup is disabled");
            return result;
        }

        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(uploadRoot)) {
            result.setMessage("Upload directory does not exist");
            return result;
        }

        Set<Path> referencedPaths = findReferencedPaths(uploadRoot);
        Instant deleteBefore = Instant.now().minus(Duration.ofHours(Math.max(orphanRetentionHours, 0)));

        try (Stream<Path> paths = Files.walk(uploadRoot)) {
            paths.filter(Files::isRegularFile)
                    .forEach(file -> processFile(file, uploadRoot, referencedPaths, deleteBefore, dryRun, result));
        } catch (IOException e) {
            log.warn("Failed to scan upload directory: {}", uploadRoot, e);
            result.setFailedCount(result.getFailedCount() + 1);
        }

        result.setMessage(dryRun ? "Dry run completed" : "Upload orphan cleanup completed");
        return result;
    }

    private Set<Path> findReferencedPaths(Path uploadRoot) {
        List<TravelMemory> memories = travelMemoryMapper.selectList(new LambdaQueryWrapper<TravelMemory>()
                .isNotNull(TravelMemory::getPhotoPath));
        Set<Path> referencedPaths = new HashSet<>();
        for (TravelMemory memory : memories) {
            Path referencedPath = normalizeReferencedPath(uploadRoot, memory.getPhotoPath());
            if (referencedPath != null && isInsideUploadRoot(referencedPath, uploadRoot)) {
                referencedPaths.add(referencedPath);
            }
        }
        return referencedPaths;
    }

    private Path normalizeReferencedPath(Path uploadRoot, String photoPath) {
        if (!StringUtils.hasText(photoPath)) {
            return null;
        }

        String normalized = photoPath.trim().replace('\\', '/');
        if (normalized.startsWith("/uploads/")) {
            return uploadRoot.resolve(normalized.substring("/uploads/".length())).toAbsolutePath().normalize();
        }
        return Paths.get(photoPath.trim()).toAbsolutePath().normalize();
    }

    private void processFile(
            Path file,
            Path uploadRoot,
            Set<Path> referencedPaths,
            Instant deleteBefore,
            boolean dryRun,
            CleanupResult result
    ) {
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!isInsideUploadRoot(normalizedFile, uploadRoot) || !isImageFile(normalizedFile)) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            return;
        }

        result.setScannedCount(result.getScannedCount() + 1);
        if (referencedPaths.contains(normalizedFile)) {
            result.setReferencedCount(result.getReferencedCount() + 1);
            result.setSkippedCount(result.getSkippedCount() + 1);
            return;
        }

        result.setOrphanCount(result.getOrphanCount() + 1);
        if (!isOlderThanRetention(normalizedFile, deleteBefore)) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            return;
        }

        if (dryRun) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            return;
        }

        try {
            Files.delete(normalizedFile);
            result.setDeletedCount(result.getDeletedCount() + 1);
        } catch (IOException e) {
            log.warn("Failed to delete orphan upload file: {}", normalizedFile, e);
            result.setFailedCount(result.getFailedCount() + 1);
        }
    }

    private boolean isOlderThanRetention(Path file, Instant deleteBefore) {
        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(file);
            return lastModifiedTime.toInstant().isBefore(deleteBefore);
        } catch (IOException e) {
            log.warn("Failed to read upload file last modified time: {}", file, e);
            return false;
        }
    }

    private boolean isInsideUploadRoot(Path path, Path uploadRoot) {
        return path.toAbsolutePath().normalize().startsWith(uploadRoot);
    }

    private boolean isImageFile(Path file) {
        String filename = file.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return false;
        }
        String extension = filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(extension);
    }
}
