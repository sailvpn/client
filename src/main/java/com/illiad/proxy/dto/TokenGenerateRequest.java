package com.illiad.proxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request object for token generation.
 * Supports 2 authentication alternatives:
 * 1. Username + Password (direct authentication)
 * 3. Existing valid token (token renewal/refresh)
 * Only ONE of the three alternatives should be provided.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenGenerateRequest {
    // Alternative 1: Username + Password
    private String username;
    private String password;

    // Alternative 3: Existing valid token (for renewal)
    private String currentToken;

    // Required for all alternatives
    // Use wrapper Long so we can detect when the client omitted this field in JSON
    private Long expirationMinutes;
}
