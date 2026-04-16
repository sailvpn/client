package com.illiad.proxy.security;

import com.illiad.proxy.config.Params;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.springframework.stereotype.Component;
import javax.net.ssl.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

/**
 * command to chrck if java trust-store has Let's Encrypt roots:
 * keytool -list -cacerts -storepass changeit | grep -iE "isrg|letsencrypt|identrust"
 */
@Component
public class Cert {
    public final SslContext sslCtx;
    public final SSLContext dtlsCtx;

    public Cert(Params params) throws Exception {
        KeyStore trustKs = KeyStore.getInstance(KeyStore.getDefaultType());
        trustKs.load(null, null);

        String pathStr = params.getCertPath();
        Path certPath = (pathStr != null && !pathStr.isEmpty()) ? Paths.get(pathStr) : null;

        if (certPath != null && Files.exists(certPath)) {
            // Logic 1: Load from specified file path
            try (InputStream in = Files.newInputStream(certPath)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Collection<? extends Certificate> certs = cf.generateCertificates(in);
                int i = 0;
                for (Certificate c : certs) {
                    trustKs.setCertificateEntry("file-cert-" + (i++), c);
                }
            }
        } else {
            // Logic 2: Fallback to System Trust-store with Let's Encrypt filter
            KeyStore systemStore = KeyStore.getInstance("JKS");
            // Default Java trust-store location and password
            String cacertsPath = System.getProperty("java.home") + "/lib/security/cacerts";
            try (InputStream is = Files.newInputStream(Paths.get(cacertsPath))) {
                systemStore.load(is, "changeit".toCharArray());
            }

            java.util.Enumeration<String> aliases = systemStore.aliases();
            int count = 0;
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (systemStore.isCertificateEntry(alias)) {
                    Certificate cert = systemStore.getCertificate(alias);
                    if (cert instanceof X509Certificate x509) {
                        String issuer = x509.getIssuerX500Principal().getName();
                        // Filter for Let's Encrypt roots (e.g., ISRG Root X1)
                        if (issuer.toUpperCase().contains("LETSENCRYPT") ||
                                issuer.toUpperCase().contains("ISRG ROOT")) {
                            trustKs.setCertificateEntry("sys-le-" + (count++), cert);
                        }
                    }
                }
            }

            if (count == 0) {
                throw new Exception("No Let's Encrypt certificates found in system trust-store.");
            }
        }

        // Initialize Managers
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustKs);

        // 1. Netty TCP Context
        this.sslCtx = SslContextBuilder.forClient()
                .trustManager(tmf)
                .sslProvider(SslProvider.OPENSSL)
                .protocols("TLSv1.2", "TLSv1.3")
                .build();

        // 2. DTLS Context (JSSE)
        this.dtlsCtx = SSLContext.getInstance("DTLS");
        dtlsCtx.init(null, tmf.getTrustManagers(), null);
    }
}