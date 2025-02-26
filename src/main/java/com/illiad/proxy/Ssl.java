package com.illiad.proxy;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;

@Component
public class Ssl {

    // Configure SSL.
    public final SslContext sslCtx = SslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE).build();

    public Ssl() throws SSLException {
    }
}
