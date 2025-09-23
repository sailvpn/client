package com.illiad.proxy.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.InputStream;
import java.security.KeyStore;

@Component
public class Dtls {

    public final SSLContext sslCtx;

    public Dtls(@Value("${proxy.ssl.trust-store}") Resource trustStore,
                @Value("${proxy.ssl.trust-store-password}") String trustStorePassword,
                @Value("${proxy.ssl.trust-store-type}") String trustStoreType) throws Exception {

        KeyStore ks = KeyStore.getInstance(trustStoreType);
        try (InputStream is = trustStore.getInputStream()) {
            ks.load(is, trustStorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, trustStorePassword.toCharArray());
        KeyManager[] keyManagers = kmf.getKeyManagers();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        TrustManager[] trustManagers = tmf.getTrustManagers();

        this.sslCtx = SSLContext.getInstance("DTLS");
        this.sslCtx.init(keyManagers, trustManagers, null);
    }

}
