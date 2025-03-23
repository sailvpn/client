package com.illiad.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Component
@ConfigurationProperties("params")
@Data
public class Params {
    int localPort = Integer.parseInt(System.getProperty("localPort", "2080"));
    String remoteHost = System.getProperty("remoteHost", "www.google.com");
    int remotePort = Integer.parseInt(System.getProperty("remotePort", "443"));
    int min = Integer.parseInt(System.getProperty("min", "1"));
    int max = Integer.parseInt(System.getProperty("max", "128"));

}
