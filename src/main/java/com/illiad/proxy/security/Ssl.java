package com.illiad.proxy.security;

import io.netty.channel.ChannelHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import java.security.cert.CertificateException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@ChannelHandler.Sharable
public class Ssl {

    // Configure SSL (Netty) for TLS clients.
    public final SslContext sslCtx;

    // Configure a javax.net.ssl.SSLContext for DTLS usage (if callers need it).
    public final SSLContext dtlsSslCtx;

    public Ssl(@Value("${proxy.ssl.trust-store}") Resource trustStore,
               @Value("${proxy.ssl.trust-store-password}") String trustStorePassword,
               @Value("${proxy.ssl.trust-store-type}") String trustStoreType) throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, KeyManagementException {
        // Load the trust store
        KeyStore ts = KeyStore.getInstance(trustStoreType);
        try (InputStream is = trustStore.getInputStream()) {
            ts.load(is, trustStorePassword.toCharArray());
        }

        // If a local dev certificate exists at ./cert/ca.crt, load and add it to the trust store
        Path certPath = Paths.get("cert", "ca.crt");
        if (Files.exists(certPath)) {
            try (InputStream certInput = Files.newInputStream(certPath)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Certificate cert = cf.generateCertificate(certInput);
                String alias = "dev-cert";
                if (ts.containsAlias(alias)) {
                    ts.deleteEntry(alias);
                }
                ts.setCertificateEntry(alias, cert);
            }
        }

        // Initialize TrustManagerFactory with the trust store
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(ts);

        // Build the Netty SslContext (TLS over TCP)
        this.sslCtx = SslContextBuilder.forClient().trustManager(trustManagerFactory).build();

        // Build a javax.net.ssl.SSLContext for DTLS (UDP) using the same trust managers
        SSLContext dtlsCtx = SSLContext.getInstance("DTLS");
        dtlsCtx.init(null, trustManagerFactory.getTrustManagers(), null);
        this.dtlsSslCtx = dtlsCtx;
    }

}
