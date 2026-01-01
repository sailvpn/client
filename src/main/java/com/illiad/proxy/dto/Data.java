package com.illiad.proxy.dto;

@lombok.Data
public class Data {
    private String expiresAt; // ISO 8601 timestamp when token expires (parseable by Instant.parse)
    private String token;
}
