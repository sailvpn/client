package com.illiad.proxy.security;

import com.illiad.proxy.dto.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@lombok.Data
@NoArgsConstructor
public class TokenHolder {
    private String token;
    private Instant expiresAt; // epoch milliseconds

    public TokenHolder(Data data) {
        this.token = data.getToken();
        this.expiresAt = Instant.parse(data.getExpiresAt());
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
