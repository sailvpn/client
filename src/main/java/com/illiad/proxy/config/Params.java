package com.illiad.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Component
@ConfigurationProperties("params")
@Data
public class Params {
    int localPort = Integer.parseInt(System.getProperty("localPort", "3080"));
    String remoteHost = System.getProperty("remoteHost", "www.google.com");
    int remotePort = Integer.parseInt(System.getProperty("remotePort", "443"));
    String crypto = System.getProperty("crypto", "SHA-256");
    int min = 1;
    int max = 128; // important, maximum value 256
    String secret = "password";

}
