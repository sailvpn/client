package com.illiad.proxy.security;

import lombok.Data;
import java.time.Instant;

@Data
public class TokenHolder {
    private String token;
    private Instant expiresAt; // epoch milliseconds

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
