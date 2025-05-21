package com.illiad.proxy.security;

import io.netty.channel.ChannelHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

@Component
@ChannelHandler.Sharable
public class Ssl {

    // Configure SSL.
    public final SslContext sslCtx;

    public Ssl(@Value("${proxy.ssl.trust-store}") Resource trustStore,
               @Value("${proxy.ssl.trust-store-password}") String trustStorePassword,
               @Value("${proxy.ssl.trust-store-type}") String trustStoreType) throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        // Load the trust store
        KeyStore ts = KeyStore.getInstance(trustStoreType);
        try (InputStream is = trustStore.getInputStream()) {
            ts.load(is, trustStorePassword.toCharArray());
        }

        // Initialize TrustManagerFactory with the trust store
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(ts);

        // Build the SslContext with the TrustManagerFactory
        this.sslCtx = SslContextBuilder.forClient().trustManager(trustManagerFactory).build();


    }

}
