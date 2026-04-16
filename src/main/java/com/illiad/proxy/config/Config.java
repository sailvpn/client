package com.illiad.proxy.config;

import com.illiad.proxy.security.Cert;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.util.Collections;

@Configuration
public class Config {
    @Bean
    public HttpClient proxyHttpClient(Params params, Cert cert) {
        ConnectionProvider cp = ConnectionProvider.builder("proxy-client-pool")
                .maxConnections(100)
                .pendingAcquireMaxCount(500)
                .build();

        HttpClient client = HttpClient.create(cp)
                .compress(true);

        // Apply Netty SslContext from Cert (trusts configured proxy certificate)
        client = client.secure(spec -> spec.sslContext(cert.sslCtx)
                .handlerConfigurator(handler -> {
                    // Access the underlying Netty SSLEngine
                    SSLEngine engine = handler.engine();

                    String sni = params.getSni();
                    if (sni == null || sni.isEmpty()) {
                        sni = params.getRemoteHost();
                    }

                    // Get existing parameters and inject your custom SNI hostname
                    SSLParameters sslParams = engine.getSSLParameters();
                    sslParams.setServerNames(Collections.singletonList(new SNIHostName(sni)));

                    engine.setSSLParameters(sslParams);
                })
        );

        return client;
    }
}
