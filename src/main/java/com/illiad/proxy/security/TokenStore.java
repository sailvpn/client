package com.illiad.proxy.security;

/**
 * Abstraction for reading/writing tokens.
 */
public interface TokenStore {
    /**
     * Read token from the store. Returns null when no token available.
     */
    String readToken() throws TokenStorageException;

    /**
     * Write token to the store. Implementations should persist the token.
     */
    void writeToken(String token) throws TokenStorageException;

    /**
     * Whether the store is writable.
     */
    boolean isWritable();
}
