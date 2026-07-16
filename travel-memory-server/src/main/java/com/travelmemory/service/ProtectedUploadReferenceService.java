package com.travelmemory.service;

import com.travelmemory.exception.BusinessException;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.security.UploadPathGuard;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProtectedUploadReferenceService {
    private final CurrentUser currentUser;

    @Value("${app.upload.dir:../uploads}")
    private String uploadDir;

    public ProtectedUploadReferenceService(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    public String requireOwnedImage(String value) {
        return requireOwnedImage(value, currentUser.requireId());
    }

    public String requireOwnedImage(String value, Long userId) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(400, "Photo URL is required");
        }
        String raw = value.trim();
        try {
            if (URI.create(raw).isAbsolute()) {
                throw new BusinessException(400, "Photo URL must use /uploads/");
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "Invalid photo URL");
        }

        String decoded;
        try {
            decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8).replace('\\', '/');
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "Invalid photo URL");
        }
        if (!decoded.startsWith("/uploads/") || decoded.contains("?") || decoded.contains("#")) {
            throw new BusinessException(400, "Photo URL must use /uploads/");
        }

        String relative = decoded.substring("/uploads/".length());
        String userPrefix = "users/" + userId + "/";
        if (relative.isBlank() || (!relative.startsWith(userPrefix) && !(userId.equals(1L) && !relative.startsWith("users/")))) {
            throw new BusinessException(404, "Photo URL not found");
        }

        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path candidate;
        try {
            candidate = root.resolve(relative).normalize();
        } catch (RuntimeException exception) {
            throw new BusinessException(400, "Invalid photo URL");
        }
        if (!UploadPathGuard.isSafeRegularFile(root, candidate)) {
            throw new BusinessException(400, "Photo URL does not reference an uploaded file");
        }
        return "/uploads/" + root.relativize(candidate).toString().replace('\\', '/');
    }
}
