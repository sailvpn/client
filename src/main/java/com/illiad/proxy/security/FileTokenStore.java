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

    public FileTokenStore(String pathStr) throws TokenStorageException {
        if (pathStr == null || pathStr.isEmpty()) {
            this.path = Paths.get("./token.jwt");
        } else {
            this.path = Paths.get(pathStr);
        }

        // Ensure parent directory and file exist (create empty file if missing).
        try {
            Path parent = this.path.getParent();
            if (parent == null) parent = Paths.get(".");
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(this.path)) {
                // create empty file if no such file exist
                Files.createFile(this.path);
            }
        } catch (IOException e) {
            throw new TokenStorageException(e.getMessage());
        }
    }

    @Override
    public String read() throws TokenStorageException {
        try {
            byte[] b = Files.readAllBytes(path);
            if (b.length == 0) return null;
            String obj = new String(b, StandardCharsets.UTF_8).trim();
            return obj.isEmpty() ? null : obj;
        } catch (IOException e) {
            throw new TokenStorageException("Failed to read token from file: " + path, e);
        }
    }

    @Override
    public void write(String obj) throws TokenStorageException {
        if (obj == null) throw new TokenStorageException("Cannot write null token");
        try {
            Path parent = path.getParent();
            // Write to temp file in the same directory, then atomically move
            Path tmp = Files.createTempFile(parent, "token", ".tmp");
            try {
                Files.writeString(tmp, obj, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException amnse) {
                    // Fallback to non-atomic move
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                // Ensure temp file is removed if it still exists
                try {
                    if (Files.exists(tmp)) Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            throw new TokenStorageException("Failed to write token to file: " + path, e);
        }
    }

    @Override
    public boolean isWritable() {
        try {
            return Files.isWritable(path);
        } catch (Exception e) {
            return false;
        }
    }
}
