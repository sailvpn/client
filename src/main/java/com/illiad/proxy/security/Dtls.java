package com.illiad.proxy.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

@Component
public class Dtls {

    public final SSLContext sslCtx;

    public Dtls(@Value("${proxy.ssl.trust-store}") Resource trustStore,
                @Value("${proxy.ssl.trust-store-password}") String trustStorePassword,
                @Value("${proxy.ssl.trust-store-type}") String trustStoreType) throws Exception {

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

        // Use the trust managers from the trust store. No key managers are provided (null) unless needed.
        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

        this.sslCtx = SSLContext.getInstance("DTLS");
        this.sslCtx.init(keyManagers, trustManagers, null);
    }

}
