package com.illiad.proxy.security;

import io.netty.handler.ssl.OpenSslContextOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

@Component
public class Cert {
    // Netty client SSL context (trusts only the configured proxy certificate)
    public final SslContext sslCtx;

    public final SSLContext dtlsCtx;

    public Cert(@Value("${proxy.ssl.cert-path:${PROXY_CERT_PATH:./certs/ca.crt}}") Resource certResource) throws Exception {
        // Resolve resource: allow Spring Resource or fallback to path(s)
        InputStream certIn = null;
        if (certResource != null && certResource.exists()) {
            certIn = certResource.getInputStream();
        } else {
            // Try default paths in order to match other code that expects ./certs/ca.crt
            String[] fallbacks = {"./certs/ca.crt", "./cert/ca.crt", "./cert/dev.crt"};
            for (String fallback : fallbacks) {
                Path p = Paths.get(fallback);
                if (Files.exists(p)) {
                    certIn = Files.newInputStream(p);
                    break;
                }
            }
        }

        if (certIn == null) {
            throw new CertificateException("Certificate file not found. Set 'proxy.ssl.cert-path' or PROXY_CERT_PATH to a PEM/.crt file containing the proxy server certificate (tried ./certs/ca.crt, ./cert/ca.crt, ./cert/dev.crt).");
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // Prepare an in-memory trust store and populate it with certs from the provided file
        KeyStore trustKs = KeyStore.getInstance(KeyStore.getDefaultType());
        trustKs.load(null, null);

        try (InputStream in = certIn) {
            Collection<? extends Certificate> certs = cf.generateCertificates(in);
            if (certs == null || certs.isEmpty()) {
                throw new CertificateException("No X.509 certificates found in provided file: ensure the file contains PEM or DER encoded X.509 certificate(s).");
            }

            int i = 0;
            for (Certificate c : certs) {
                trustKs.setCertificateEntry("proxy-cert-" + (i++), c);
            }
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustKs);
        TrustManager[] trustManagers = tmf.getTrustManagers();

        // 1. Build standard TCP SslContext
        this.sslCtx = SslContextBuilder.forClient()
                .trustManager(tmf)
                .sslProvider(SslProvider.OPENSSL) // Match the server's provider
                .protocols("TLSv1.2", "TLSv1.3")  // Explicitly allow both
                .build();

        // 2. Create the DTLS Context using JSSE provider
        this.dtlsCtx = SSLContext.getInstance("DTLS");
        dtlsCtx.init(null, trustManagers, null);

    }

}

