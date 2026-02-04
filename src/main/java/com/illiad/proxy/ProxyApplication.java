package com.illiad.proxy;

import io.netty.util.ResourceLeakDetector;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"com.illiad.proxy"})
public class ProxyApplication {
	public static void main(String[] args) {
		// direct memory leakage test
		// ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
		SpringApplication.run(ProxyApplication.class, args);
	}

}
