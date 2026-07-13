package com.travelmemory.security;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class UploadPathGuard {
    private UploadPathGuard() {
    }

    public static boolean isSafeRegularFile(Path root, Path file) {
        if (root == null || file == null || !file.startsWith(root) || file.equals(root)) {
            return false;
        }
        if (Files.isSymbolicLink(root)) {
            return false;
        }

        Path current = root;
        for (Path segment : root.relativize(file)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                return false;
            }
        }
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
    }
}
