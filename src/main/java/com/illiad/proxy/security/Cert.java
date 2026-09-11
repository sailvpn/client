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
            try (InputStream in = Files.newInputStream(certPath)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Collection<? extends Certificate> certs = cf.generateCertificates(in);
                int i = 0;
                for (Certificate c : certs) {
                    trustKs.setCertificateEntry("file-cert-" + (i++), c);
                }
            }
        } else {
            // Load full standard default system trust authorities
            KeyStore systemStore = KeyStore.getInstance("JKS");
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
                        String issuer = x509.getIssuerX500Principal().getName().toUpperCase();
                        String subject = x509.getSubjectX500Principal().getName().toUpperCase();

                        // 👍 FIXED FILTER: Captures Let's Encrypt Roots, Intermediary authorities (R3, E1)
                        // and standard digital cross-signing anchors (IdenTrust) natively
                        if (issuer.contains("LETSENCRYPT") || issuer.contains("ISRG ROOT") ||
                                subject.contains("LETSENCRYPT") || subject.contains("ISRG ROOT") ||
                                issuer.contains("IDENTRUST") || subject.contains("IDENTRUST")) {
                            trustKs.setCertificateEntry("sys-le-" + (count++), cert);
                        }
                    }
                }
            }

            if (count == 0) {
                // Safe Fallback: If strict filtration produces nothing, populate the complete system store
                java.util.Enumeration<String> fallbackAliases = systemStore.aliases();
                while (fallbackAliases.hasMoreElements()) {
                    String alias = fallbackAliases.nextElement();
                    if (systemStore.isCertificateEntry(alias)) {
                        trustKs.setCertificateEntry("fallback-" + alias, systemStore.getCertificate(alias));
                    }
                }
            }
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustKs);

        // 1. 👍 FIXED: Swapped SslProvider.OPENSSL for SslProvider.JDK
        // Removes all cross-platform netty-tcnative binary crashes from user machines
        this.sslCtx = SslContextBuilder.forClient()
                .trustManager(tmf)
                .sslProvider(SslProvider.JDK) // 🚀 NATIVE JAVA HARDWARE ACCELERATED EXTENSION
                .protocols("TLSv1.2", "TLSv1.3")
                .build();

        // 2. DTLS Context (JSSE)
        this.dtlsCtx = SSLContext.getInstance("DTLS");
        dtlsCtx.init(null, tmf.getTrustManagers(), null);
    }
}
