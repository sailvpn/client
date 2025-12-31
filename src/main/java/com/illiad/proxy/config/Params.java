package com.illiad.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Component
@ConfigurationProperties("params")
@Data
public class Params {
    int localPort = Integer.parseInt(System.getProperty("localPort", "3080"));
    String localHost = System.getProperty("localHost", "127.0.0.1");
    int httpPort = Integer.parseInt(System.getProperty("httpPort", "9999"));
    String remoteHost = System.getProperty("remoteHost", "127.0.0.1");
    int remotePort = Integer.parseInt(System.getProperty("remotePort", "2080"));
    String udpHost = System.getProperty("udpHost", "127.0.0.1");
    // crypto name as defined in Cryptos
    String crypto = System.getProperty("crypto", "SHA_256");
    int min = 1;
    int max = 64; // important, maximum value 128
    String secret = "password";

    // NOTE: in-memory jwtToken removed to avoid embedding secrets in configuration objects.
    // The client must use a TokenStore implementation (e.g. FileTokenStore) to load/save tokens
    // from a file on disk. This path can be configured via params.jwtTokenFile, system property
    // -DjwtTokenFile=... or environment variable JWT_TOKEN_FILE.

    // JWT token file path: can be set via application.properties (params.jwtTokenFile)
    // or overridden via JVM system property -DjwtTokenFile=... or environment JWT_TOKEN_FILE
    String jwtTokenFile = System.getProperty("jwtTokenFile", System.getenv("JWT_TOKEN_FILE") != null ? System.getenv("JWT_TOKEN_FILE") : "./token.jwt");

    // JWT auto-acquisition settings
    String username = System.getProperty("username", "");
    String password = System.getProperty("password", "");
    int tokenExpirationMinutes = Integer.parseInt(System.getProperty("tokenExpirationMinutes", "43200")); // 30 days default

    // JWT token mode: use TokenMode enum instead of raw string
    String tokenMode = System.getProperty("tokenMode", "MANUAL"); // kept as raw property for Spring binding/backwards compatibility
    // JWT auto-renewal settings
    int renewInterval = Integer.parseInt(System.getProperty("tokenRenewalIntervalMinutes", "30")); // 30 minutes default
}
