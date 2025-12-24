package com.illiad.proxy.security;

/**
 * Exception for token storage/read/write failures.
 */
public class TokenStorageException extends Exception {
    public TokenStorageException(String msg) { super(msg); }
    public TokenStorageException(String msg, Throwable cause) { super(msg, cause); }
}

