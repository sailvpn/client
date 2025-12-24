package com.illiad.proxy.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Simple file-backed token store. Path configurable via constructor.
 * Defaults to ./token.jwt when null/empty path provided.
 */
public class FileTokenStore implements TokenStore {
    private final Path path;

    public FileTokenStore(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) {
            this.path = Paths.get("./token.jwt");
        } else {
            this.path = Paths.get(pathStr);
        }
    }

    @Override
    public String readToken() throws TokenStorageException {
        try {
            if (!Files.exists(path)) return null;
            byte[] b = Files.readAllBytes(path);
            if (b == null || b.length == 0) return null;
            String s = new String(b, StandardCharsets.UTF_8).trim();
            return s.isEmpty() ? null : s;
        } catch (IOException e) {
            throw new TokenStorageException("Failed to read token from file: " + path, e);
        }
    }

    @Override
    public void writeToken(String token) throws TokenStorageException {
        if (token == null) throw new TokenStorageException("Cannot write null token");
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        try {
            Path parent = path.getParent();
            if (parent == null) parent = Paths.get(".");
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // Write to temp file in the same directory, then atomically move
            Path tmp = Files.createTempFile(parent, "token", ".tmp");
            try {
                Files.write(tmp, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException amnse) {
                    // Fallback to non-atomic move
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                // Ensure temp file is removed if it still exists
                try { if (Files.exists(tmp)) Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            throw new TokenStorageException("Failed to write token to file: " + path, e);
        }
    }

    @Override
    public boolean isWritable() {
        try {
            Path parent = path.getParent();
            if (parent == null) parent = Paths.get(".");
            if (!Files.exists(parent)) {
                // try to create and delete a marker file to verify writability
                try {
                    Files.createDirectories(parent);
                } catch (IOException e) {
                    return false;
                }
            }

            // If file exists, check writability on file; otherwise check parent dir writability
            if (Files.exists(path)) {
                return Files.isWritable(path);
            } else {
                return Files.isWritable(parent);
            }
        } catch (Exception e) {
            return false;
        }
    }
}
