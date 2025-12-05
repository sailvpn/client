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
    int max = 128; // important, maximum value 256
    String secret = "password";

    // JWT token for authentication (optional - only used when crypto=JWT)
    String jwtToken = System.getProperty("jwtToken", "");

    // JWT auto-acquisition settings
    String username = System.getProperty("username", "");
    String password = System.getProperty("password", "");
    int tokenExpirationMinutes = Integer.parseInt(System.getProperty("tokenExpirationMinutes", "43200")); // 30 days default

    // JWT token mode: "auto" or "manual"
    // - "auto": Client acquires and renews tokens automatically using username/password
    // - "manual": Client uses provided jwtToken, no auto-renewal (for token sharing)
    String tokenMode = System.getProperty("tokenMode", "auto"); // default: auto

    // JWT auto-renewal settings
    boolean tokenRenewalEnabled = Boolean.parseBoolean(System.getProperty("tokenRenewalEnabled", "true"));
    int tokenRenewalIntervalMinutes = Integer.parseInt(System.getProperty("tokenRenewalIntervalMinutes", "10")); // 10 minutes default

}
