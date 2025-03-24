package com.illiad.proxy.security;

public interface Secret {
    byte[] getSecret();

    boolean verify(byte[] secret);
}
