package com.illiad.proxy.security;

import com.illiad.proxy.config.Params;
import org.springframework.stereotype.Component;

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
 * JWT Token Manager
 * Handles automatic token acquisition and renewal for the proxy client
 */
@Component
public class JwtTokenManager {
    private final Params params;
    private final AtomicReference<String> currentToken = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = false;

    public JwtTokenManager(Params params) {
        this.params = params;
    }

    /**
     * Initialize token management - acquire initial token and start renewal
     */
    public void initialize() {
        if (!params.getCrypto().equals("JWT")) {
            System.out.println("JWT token management disabled (crypto != JWT)");
            return;
        }

        // Check if we should auto-acquire token
        if (params.getUsername() != null && !params.getUsername().isEmpty() &&
            params.getPassword() != null && !params.getPassword().isEmpty()) {
            
            System.out.println("Auto-acquiring JWT token for user: " + params.getUsername());
            
            // Acquire initial token
            if (acquireToken()) {
                System.out.println("Successfully acquired JWT token");
                
                // Start periodic renewal if enabled
                if (params.isTokenRenewalEnabled()) {
                    startPeriodicRenewal();
                }
            } else {
                System.err.println("Failed to acquire initial JWT token");
                // Fall back to configured token if available
                if (params.getJwtToken() != null && !params.getJwtToken().isEmpty()) {
                    currentToken.set(params.getJwtToken());
                    System.out.println("Using pre-configured JWT token from properties");
                } else {
                    throw new IllegalStateException("Failed to acquire JWT token and no fallback token configured");
                }
            }
        } else if (params.getJwtToken() != null && !params.getJwtToken().isEmpty()) {
            // Use pre-configured token
            currentToken.set(params.getJwtToken());
            System.out.println("Using pre-configured JWT token");
            
            // Still enable renewal if configured
            if (params.isTokenRenewalEnabled()) {
                startPeriodicRenewal();
            }
        } else {
            throw new IllegalStateException("JWT authentication enabled but no credentials or token provided");
        }
    }

    /**
     * Acquire a new JWT token from the server using username/password
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
                return true;
            }
            
            System.err.println("Failed to extract token from response: " + response);
            return false;
            
        } catch (Exception e) {
            System.err.println("Error acquiring JWT token: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Renew the current JWT token
     */
    private boolean renewToken() {
        try {
            String token = currentToken.get();
            if (token == null || token.isEmpty()) {
                System.err.println("No current token to renew");
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
                System.out.println("JWT token renewed successfully at " + new java.util.Date());
                return true;
            }
            
            System.err.println("Failed to extract renewed token from response: " + response);
            return false;
            
        } catch (Exception e) {
            System.err.println("Error renewing JWT token: " + e.getMessage());
            e.printStackTrace();
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
        
        System.out.println("Starting JWT token renewal every " + renewalIntervalMinutes + " minutes");
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("Attempting to renew JWT token...");
                renewToken();
            } catch (Exception e) {
                System.err.println("Error in token renewal task: " + e.getMessage());
                e.printStackTrace();
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
     * Get the current JWT token
     */
    public String getCurrentToken() {
        String token = currentToken.get();
        if (token == null || token.isEmpty()) {
            // Fall back to configured token
            return params.getJwtToken();
        }
        return token;
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

    /**
     * Send HTTP POST request with redirect handling
     * @param urlString Target URL
     * @param jsonBody JSON request body
     * @param redirectCount Number of redirects followed (to prevent infinite loops)
     * @return Response body
     * @throws Exception if request fails
     */
    private String sendHttpPost(String urlString, String jsonBody, int redirectCount) throws Exception {
        // Prevent infinite redirect loops
        if (redirectCount > 5) {
            throw new Exception("Too many redirects (>5). Possible redirect loop.");
        }

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(false); // Handle redirects manually

            // Disable SSL certificate validation for self-signed certificates
            if (urlString.startsWith("https")) {
                disableSslVerification(conn);
            }
            
            // Write request body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            // Read response
            int responseCode = conn.getResponseCode();
            
            // Handle redirects (307 Temporary Redirect)
            if (responseCode == 307 || responseCode == 308) {
                String location = conn.getHeaderField("Location");
                if (location == null || location.isEmpty()) {
                    throw new Exception("HTTP " + responseCode + " redirect but no Location header provided");
                }

                System.out.println("Following HTTP " + responseCode + " redirect to: " + location);

                // Handle relative URLs
                if (!location.startsWith("http://") && !location.startsWith("https://")) {
                    URL originalUrl = new URL(urlString);
                    if (location.startsWith("/")) {
                        // Absolute path
                        location = originalUrl.getProtocol() + "://" + originalUrl.getHost() +
                                  (originalUrl.getPort() != -1 ? ":" + originalUrl.getPort() : "") + location;
                    } else {
                        // Relative path
                        String basePath = originalUrl.getPath();
                        int lastSlash = basePath.lastIndexOf('/');
                        basePath = lastSlash >= 0 ? basePath.substring(0, lastSlash + 1) : "/";
                        location = originalUrl.getProtocol() + "://" + originalUrl.getHost() +
                                  (originalUrl.getPort() != -1 ? ":" + originalUrl.getPort() : "") + basePath + location;
                    }
                }

                // Follow redirect with same body
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

    /**
     * Disable SSL certificate verification (for self-signed certificates)
     */
    private void disableSslVerification(HttpURLConnection conn) {
        try {
            if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                javax.net.ssl.HttpsURLConnection httpsConn = (javax.net.ssl.HttpsURLConnection) conn;
                
                // Create a trust manager that accepts all certificates
                javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
                };
                
                javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                
                // Create hostname verifier that accepts all hostnames
                httpsConn.setHostnameVerifier((hostname, session) -> true);
            }
        } catch (Exception e) {
            System.err.println("Failed to disable SSL verification: " + e.getMessage());
        }
    }

    /**
     * Extract token from JSON response (simple parsing without external library)
     */
    private String extractTokenFromResponse(String jsonResponse) {
        try {
            // Look for "token":"..." pattern
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
            System.err.println("Error extracting token from response: " + e.getMessage());
            return null;
        }
    }
}

