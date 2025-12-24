package com.illiad.proxy.security;

import com.illiad.proxy.config.Params;
import com.illiad.proxy.config.TokenMode;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Token Manager
 * Handles automatic token acquisition and renewal for the proxy client
 */
@Component
public class TokenManager {
    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);
    private final Params params;
    private final AtomicReference<String> currentToken = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = false;
    private final TokenStore tokenStore;

    @Autowired
    public TokenManager(Params params) {
        this.params = params;
        // Determine path priority: Params (application.properties) > env JWT_TOKEN_FILE > system property jwtTokenFile > default
        String configured = params.getJwtTokenFile();
        if (configured != null && !configured.isEmpty()) {
            this.tokenStore = new FileTokenStore(configured);
        } else {
            String env = System.getenv("JWT_TOKEN_FILE");
            if (env != null && !env.isEmpty()) {
                this.tokenStore = new FileTokenStore(env);
            } else {
                String prop = System.getProperty("jwtTokenFile");
                if (prop != null && !prop.isEmpty()) {
                    this.tokenStore = new FileTokenStore(prop);
                } else {
                    this.tokenStore = new FileTokenStore("./token.jwt");
                }
            }
        }
    }

    // For tests or explicit wiring (package-private)
    TokenManager(Params params, TokenStore store) {
        this.params = params;
        this.tokenStore = store;
    }

    /**
     * Initialize token management - acquire initial token and start renewal
     */
    public void initialize() {
        if (!params.getCrypto().equals("JWT")) {
            log.info("Token management disabled (crypto != JWT)");
            return;
        }

        // Attempt to read existing token from TokenStore (if any)
        try {
            String stored = tokenStore != null ? tokenStore.readToken() : null;
            if (stored != null && !stored.isEmpty()) {
                currentToken.set(stored);
                log.info("Loaded token from token store");
            }
        } catch (TokenStorageException e) {
            log.error("Failed to read token from store: {}", e.getMessage(), e);
        }

        TokenMode tokenMode = TokenMode.fromString(params.getTokenMode());

        if (tokenMode == null) {
            throw new IllegalStateException("Invalid tokenMode: null. Must be 'auto' or 'manual'");
        }

        if (tokenMode.isAuto()) {
            // Automatic mode - require username/password
            if (params.getUsername() == null || params.getUsername().isEmpty() ||
                params.getPassword() == null || params.getPassword().isEmpty()) {
                throw new IllegalStateException(
                    "Automatic token mode (tokenMode=auto) requires username and password. " +
                    "Either provide credentials or set tokenMode=manual"
                );
            }

            log.info("Automatic token mode enabled (tokenMode=auto)");
            log.info("Auto-acquiring token for user: {}", params.getUsername());

            // Acquire initial token
            if (acquireToken()) {
                log.info("Successfully acquired token");

                // Persist token
                try {
                    if (tokenStore != null && tokenStore.isWritable()) {
                        tokenStore.writeToken(currentToken.get());
                        log.info("Persisted token to token store");
                    }
                } catch (TokenStorageException e) {
                    log.error("Failed to persist token: {}", e.getMessage(), e);
                }
                // Start periodic renewal if enabled
                if (params.isTokenRenewalEnabled()) {
                    startPeriodicRenewal();
                } else {
                    log.info("Token auto-renewal is disabled (tokenRenewalEnabled=false)");
                }
            } else {
                log.error("Failed to acquire initial token");
                // Fall back to token from token store if available
                try {
                    String stored = tokenStore != null ? tokenStore.readToken() : null;
                    if (stored != null && !stored.isEmpty()) {
                        currentToken.set(stored);
                        log.info("Using token from token store (fallback)");
                    } else {
                        throw new IllegalStateException("Failed to acquire token and no fallback token available in token store");
                    }
                } catch (TokenStorageException e) {
                    throw new IllegalStateException("Failed to acquire token and failed to read token store", e);
                }
            }
        } else { // MANUAL
            // Manual mode - rely on token present in token store
            try {
                String stored = tokenStore != null ? tokenStore.readToken() : null;
                if (stored == null || stored.isEmpty()) {
                    throw new IllegalStateException(
                        "Manual token mode (tokenMode=manual) requires a token present in the configured token store/file. " +
                        "Please create the token file with the proxy server token."
                    );
                }
                currentToken.set(stored);
                log.info("Manual token mode enabled (tokenMode=manual)");
                log.info("Using token from token store");
                log.info("Auto-renewal is DISABLED in manual mode");
            } catch (TokenStorageException e) {
                throw new IllegalStateException("Manual mode requires a readable token store", e);
            }
        }
    }

    /**
     * Acquire a new token from the server using username/password
     */
    private boolean acquireToken() {
        try {
            String serverUrl = buildServerUrl() + "/api/auth/token/generate";

            // Build JSON request body
            String jsonBody = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\",\"expirationMinutes\":%d}",
                params.getUsername(),
                params.getPassword(),
                params.getTokenExpirationMinutes()
            );

            String response = sendHttpPost(serverUrl, jsonBody);

            // Parse response (simple JSON parsing without external library)
            String token = extractTokenFromResponse(response);
            if (token != null && !token.isEmpty()) {
                currentToken.set(token);
                // persist
                try {
                    if (tokenStore != null && tokenStore.isWritable()) {
                        tokenStore.writeToken(token);
                    }
                } catch (TokenStorageException e) {
                    log.error("Failed to persist token after acquire: {}", e.getMessage(), e);
                }
                return true;
            }

            log.error("Failed to extract token from response: {}", response);
            return false;

        } catch (Exception e) {
            log.error("Error acquiring token: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Renew the current token
     */
    private boolean renewToken() {
        try {
            String token = currentToken.get();
            if (token == null || token.isEmpty()) {
                log.error("No current token to renew");
                return acquireToken(); // Fall back to acquiring new token
            }

            String serverUrl = buildServerUrl() + "/api/auth/token/generate";

            // Build JSON request body
            String jsonBody = String.format(
                "{\"currentToken\":\"%s\",\"expirationMinutes\":%d}",
                token,
                params.getTokenExpirationMinutes()
            );

            String response = sendHttpPost(serverUrl, jsonBody);

            // Parse response
            String newToken = extractTokenFromResponse(response);
            if (newToken != null && !newToken.isEmpty()) {
                currentToken.set(newToken);
                log.info("Token renewed successfully at {}", new java.util.Date());
                try {
                    if (tokenStore != null && tokenStore.isWritable()) {
                        tokenStore.writeToken(newToken);
                    }
                } catch (TokenStorageException e) {
                    log.error("Failed to persist token after renew: {}", e.getMessage(), e);
                }
                return true;
            }

            log.error("Failed to extract renewed token from response: {}", response);
            return false;

        } catch (Exception e) {
            log.error("Error renewing token: {}", e.getMessage(), e);
            // Try to acquire new token if renewal fails
            return acquireToken();
        }
    }

    /**
     * Start periodic token renewal
     */
    private void startPeriodicRenewal() {
        if (running) {
            return;
        }

        running = true;
        long renewalIntervalMinutes = params.getTokenRenewalIntervalMinutes();

        log.info("Starting token renewal every {} minutes", renewalIntervalMinutes);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("Attempting to renew token...");
                renewToken();
            } catch (Exception e) {
                log.error("Error in token renewal task: {}", e.getMessage(), e);
            }
        }, renewalIntervalMinutes, renewalIntervalMinutes, TimeUnit.MINUTES);
    }

    /**
     * Stop token renewal
     */
    public void shutdown() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Get the current token
     */
    public String getCurrentToken() {
        String token = currentToken.get();
        if (token == null || token.isEmpty()) {
            // Fall back to reading from token store
            try {
                String stored = tokenStore != null ? tokenStore.readToken() : null;
                return stored;
            } catch (TokenStorageException e) {
                log.error("Failed to read token store as fallback: {}", e.getMessage(), e);
                return null;
            }
        }
        return token;
    }

    /**
     * Insert or replace the current token and persist it to the configured TokenStore (if writable).
     * Caller will receive a TokenStorageException when persistence fails.
     */
    public void setToken(String token) throws TokenStorageException {
        if (token == null || token.isEmpty()) throw new IllegalArgumentException("token must be non-empty");
        currentToken.set(token);
        if (tokenStore != null && tokenStore.isWritable()) {
            tokenStore.writeToken(token);
        } else if (tokenStore == null) {
            throw new TokenStorageException("No token store configured to persist token");
        } else {
            throw new TokenStorageException("Token store is not writable: " + tokenStore.getClass().getName());
        }
    }

    /**
     * Build server URL based on configuration
     */
    private String buildServerUrl() {
        String protocol = params.getRemotePort() == 443 ? "https" : "http";
        return protocol + "://" + params.getRemoteHost() + ":" + params.getRemotePort();
    }

    /**
     * Send HTTP POST request with redirect handling
     */
    private String sendHttpPost(String urlString, String jsonBody) throws Exception {
        return sendHttpPost(urlString, jsonBody, 0);
    }

    private String sendHttpPost(String urlString, String jsonBody, int redirectCount) throws Exception {
        if (redirectCount > 5) {
            throw new Exception("Too many redirects (>5). Possible redirect loop.");
        }

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type","application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(false);

            // Disable SSL certificate validation for self-signed certificates
            if (urlString.startsWith("https")) {
                disableSslVerification(conn);
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == 307 || responseCode == 308) {
                String location = conn.getHeaderField("Location");
                if (location == null || location.isEmpty()) {
                    throw new Exception("HTTP " + responseCode + " redirect but no Location header provided");
                }

                log.info("Following HTTP {} redirect to: {}", responseCode, location);

                if (!location.startsWith("http://") && !location.startsWith("https://")) {
                    URL originalUrl = new URL(urlString);
                    if (location.startsWith("/")) {
                        location = originalUrl.getProtocol() + "://" + originalUrl.getHost() +
                                  (originalUrl.getPort() != -1 ? ":" + originalUrl.getPort() : "") + location;
                    } else {
                        String basePath = originalUrl.getPath();
                        int lastSlash = basePath.lastIndexOf('/');
                        basePath = lastSlash >= 0 ? basePath.substring(0, lastSlash + 1) : "/";
                        location = originalUrl.getProtocol() + "://" + originalUrl.getHost() +
                                  (originalUrl.getPort() != -1 ? ":" + originalUrl.getPort() : "") + basePath + location;
                    }
                }

                return sendHttpPost(location, jsonBody, redirectCount + 1);
            }

            BufferedReader br;
            if (responseCode >= 200 && responseCode < 300) {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            } else {
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            }

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();

            if (responseCode < 200 || responseCode >= 300) {
                throw new Exception("HTTP request failed with status " + responseCode + ": " + response);
            }

            return response.toString();

        } finally {
            conn.disconnect();
        }
    }

    private void disableSslVerification(HttpURLConnection conn) {
        try {
            if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                javax.net.ssl.HttpsURLConnection httpsConn = (javax.net.ssl.HttpsURLConnection) conn;

                javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    }
                };

                javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                httpsConn.setSSLSocketFactory(sc.getSocketFactory());

                httpsConn.setHostnameVerifier((hostname, session) -> true);
            }
        } catch (Exception e) {
            log.error("Failed to disable SSL verification: {}", e.getMessage(), e);
        }
    }

    private String extractTokenFromResponse(String jsonResponse) {
        try {
            String tokenPattern = "\"token\":\"";
            int tokenStart = jsonResponse.indexOf(tokenPattern);
            if (tokenStart == -1) {
                return null;
            }

            tokenStart += tokenPattern.length();
            int tokenEnd = jsonResponse.indexOf("\"", tokenStart);
            if (tokenEnd == -1) {
                return null;
            }

            return jsonResponse.substring(tokenStart, tokenEnd);
        } catch (Exception e) {
            log.error("Error extracting token from response: {}", e.getMessage(), e);
            return null;
        }
    }
}

