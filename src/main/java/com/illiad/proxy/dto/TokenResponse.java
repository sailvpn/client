package com.illiad.proxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response object for token generation endpoint.
 * Contains the generated JWT token and its expiration time.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenResponse {
    private String token;
    private String expiresAt;  // ISO 8601 timestamp when token expires (parseable by Instant.parse)
}
