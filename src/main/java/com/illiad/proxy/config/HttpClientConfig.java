package com.illiad.proxy.config;

import com.illiad.proxy.security.Cert;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {
    @Bean
    public HttpClient proxyHttpClient(Cert cert) {
        ConnectionProvider cp = ConnectionProvider.builder("proxy-client-pool")
                .maxConnections(100)
                .pendingAcquireMaxCount(500)
                .build();

        HttpClient client = HttpClient.create(cp)
                .compress(true);

        // Apply Netty SslContext from Cert (trusts configured proxy certificate)
        if (cert != null) {
            client = client.secure(spec -> spec.sslContext(cert.sslCtx));
        }

        return client;
    }
}
