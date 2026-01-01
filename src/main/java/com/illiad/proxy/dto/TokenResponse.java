package com.illiad.proxy.dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Response object for token generation endpoint.
 * Contains the generated JWT token and its expiration time.
 */
@lombok.Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenResponse {
    private Boolean success;
    private int reasonCode; // numeric reason code for programmatic handling (0 == OK/unset)
    private Data data;
}
