package com.illiad.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Component
@ConfigurationProperties("params")
@Data
public class Params {
    // Default values are set directly; Spring overrides them if found in a config file
    private int localPort = 3080;
    private String localHost = "127.0.0.1";
    private int httpPort = 9999;
    private String remoteHost = "127.0.0.1";
    private String sni = "example.test";
    private int remotePort = 5001;
    // crypto name as defined in Cryptos
    private String crypto = "SHA_256";
    private int min = 1;
    private int max = 64; // important, maximum value 128
    String secret = "password";

    private String certPath = "./ca.crt";
    private String jwtTokenFile = "./troad.jwt";

    // JWT auto-acquisition settings
    private String username = "";
    private String password = "";
    private Long expireMins = 43200L; // 30 days default

    // JWT token mode: use TokenMode enum instead of raw string
    private String tokenMode = "MANUAL"; // kept as raw property for Spring binding/backwards compatibility
    // JWT auto-renewal settings
    private Long renewInterval = 30L; // 30 minutes default
}
