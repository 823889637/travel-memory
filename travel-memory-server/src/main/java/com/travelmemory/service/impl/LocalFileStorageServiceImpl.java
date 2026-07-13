package com.travelmemory.service.impl;

import com.travelmemory.common.StoredFile;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.service.FileStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "heic", "heif");

    @Value("${app.upload.dir:../uploads}")
    private String uploadDir;

    @Override
    public StoredFile store(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(401, "Authentication is required");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "Only image files are allowed");
        }

        String datePath = "users/" + userId + "/" + LocalDate.now().format(DATE_PATH_FORMATTER);
        String storedFilename = UUID.randomUUID() + "." + extension;
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetDirectory = uploadRoot.resolve(datePath).normalize();
        Path targetFile = targetDirectory.resolve(storedFilename).normalize();
        if (!targetDirectory.startsWith(uploadRoot) || !targetFile.startsWith(uploadRoot)) {
            throw new BusinessException(400, "Invalid upload path");
        }

        try {
            Files.createDirectories(targetDirectory);
            file.transferTo(targetFile);
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to save uploaded file");
        }

        String url = "/uploads/" + datePath.replace("\\", "/") + "/" + storedFilename;
        return new StoredFile(url, targetFile.toString());
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new BusinessException(400, "Image file extension is required");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
