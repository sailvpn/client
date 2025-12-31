package com.illiad.proxy.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.config.TokenMode;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Token Manager
 * Handles automatic token acquisition and renewal for the proxy client
 */
@Component
public class TokenManager {
    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);
    private final Params params;
    private final ObjectMapper objMapper = createObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = false;
    private final TokenStore tokenStore;

    // Holds the last known token and its expiresAt
    private final AtomicReference<TokenHolder> current = new AtomicReference<>();

    @Autowired
    public TokenManager(Params params) throws TokenStorageException, JsonProcessingException {
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

        // Load existing token holder from store if present
        String stored = tokenStore.read();
        if (stored == null || stored.isEmpty()) {
            current.set(emptyHolder());
        } else {
            TokenHolder holder = objMapper.readValue(stored, TokenHolder.class);
            current.set(Objects.requireNonNullElseGet(holder, TokenManager::emptyHolder));
        }
    }

    // For tests or explicit wiring (package-private)
    TokenManager(Params params, TokenStore store) {
        this.params = params;
        this.tokenStore = store;
        this.current.set(emptyHolder());
    }

    private static TokenHolder emptyHolder() {
        TokenHolder h = new TokenHolder();
        h.setToken(null);
        h.setExpiresAt(Instant.EPOCH);
        return h;
    }

    /**
     * Initialize token management - acquire initial token and start renewal
     */
    public void initialize() {
        if (!Objects.equals(params.getCrypto(), "JWT")) {
            log.info("Token management disabled (crypto != JWT)");
            return;
        }

        if (TokenMode.AUTO == TokenMode.valueOf(params.getTokenMode())) {
            log.info("Automatic token mode enabled (tokenMode=auto)");

            // If no token present or expired => try to acquire via username/password
            if (current.get().isExpired()) {
                log.info("No usable token found or token expired; attempting credential-based acquisition");
                if (!acquireTokenWithCredentialsOrThrow())
                    return; // acquireTokenWithCredentialsOrThrow will throw on failure

                // start renewal if enabled
                startAutoRenewal();
                return;
                // Token not expired. Check remaining validity.
            } else if (Duration.between(Instant.now(), current.get().getExpiresAt()).toMinutes() < params.getRenewInterval() * 5L) {
                log.info("Token expired; will acquire via credentials");
                if (!renewTokenOrThrow()) return;
            } else {
                log.info("Token is valid ({} minutes remaining). Using current token", minutesRemaining);
                // persist current holder to ensure store has latest
                try {
                    persistCurrentHolder();
                } catch (TokenStorageException e) {
                    log.error("Failed to persist token store during initialization: {}", e.getMessage(), e);
                }
            }


            if (params.isTokenRenewalEnabled()) startPeriodicRenewal();

        } else { // MANUAL
            // Manual mode - rely on token present in token store
            try {
                String stored = tokenStore != null ? tokenStore.read() : null;
                if (stored == null || stored.isEmpty()) {
                    throw new IllegalStateException(
                        "Manual token mode (tokenMode=manual) requires a token present in the configured token store/file. " +
                        "Please create the token file with the proxy server token."
                    );
                }
                TokenHolder holder = objMapper.readValue(stored, TokenHolder.class);
                if (holder == null || holder.getToken() == null || holder.getToken().isEmpty()) {
                    throw new IllegalStateException("Manual mode token file does not contain a valid token");
                }
                current.set(holder);
                log.info("Manual token mode enabled (tokenMode=manual)");
                log.info("Using token from token store");
                log.info("Auto-renewal is DISABLED in manual mode");
            } catch (TokenStorageException e) {
                throw new IllegalStateException("Manual mode requires a readable token store", e);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse token from token store", e);
            }
        }
    }

    /**
     * Acquire a new token from the server using username/password. Throws on failure.
     */
    private boolean acquireTokenWithCredentialsOrThrow() {
        if (params.getUsername() == null || params.getUsername().isEmpty() ||
            params.getPassword() == null || params.getPassword().isEmpty()) {
            throw new IllegalStateException("Automatic token mode requires username/password when no valid token is available");
        }

        boolean ok = acquireToken();
        if (!ok) {
            throw new IllegalStateException("Failed to acquire token using provided username/password");
        }
        return true;
    }

    private boolean renewTokenOrThrow() {
        boolean ok = renewToken();
        if (!ok) {
            // Try to acquire with credentials as fallback
            log.warn("Renewal failed; attempting credential-based acquisition as fallback");
            return acquireTokenWithCredentialsOrThrow();
        }
        return true;
    }

    /**
     * Send HTTP POST request using Netty HttpClient
     */
    private Mono<String> sendHttpPost(String urlString, String jsonBody) {
        HttpClient client = HttpClient.create();

        return client.post()
                .uri(urlString)
                .send(Mono.just(jsonBody).map(body -> body.getBytes(StandardCharsets.UTF_8)))
                .responseSingle((response, byteBufMono) -> {
                    if (response.status().code() >= 200 && response.status().code() < 300) {
                        return byteBufMono.asString(StandardCharsets.UTF_8);
                    } else {
                        return byteBufMono.asString(StandardCharsets.UTF_8)
                                .flatMap(body -> Mono.error(new RuntimeException("HTTP request failed with status " + response.status().code() + ": " + body)));
                    }
                });
    }

    /**
     * Acquire a new token from the server using username/password (non-throwing variant)
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

            String response = sendHttpPost(serverUrl, jsonBody).block();

            // Parse response into TokenHolder (expects data.expiresAt and data.token)
            TokenHolder holder = parseTokenHolderFromApiResponse(response);
            if (holder != null && holder.getToken() != null && !holder.getToken().isEmpty()) {
                current.set(holder);
                try {
                    persistCurrentHolder();
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
     * Renew the current token (non-throwing variant). Tries renew API with current token and updates store.
     */
    private boolean renewToken() {
        try {
            TokenHolder holder = current.get();
            if (holder == null || holder.getToken() == null || holder.getToken().isEmpty()) {
                log.error("No current token to renew");
                return false;
            }

            String serverUrl = buildServerUrl() + "/api/auth/token/generate";

            // Build JSON request body for renewal
            String jsonBody = String.format(
                "{\"currentToken\":\"%s\",\"expirationMinutes\":%d}",
                holder.getToken(),
                params.getTokenExpirationMinutes()
            );

            String response = sendHttpPost(serverUrl, jsonBody).block();

            TokenHolder newHolder = parseTokenHolderFromApiResponse(response);
            if (newHolder != null && newHolder.getToken() != null && !newHolder.getToken().isEmpty()) {
                current.set(newHolder);
                try {
                    persistCurrentHolder();
                } catch (TokenStorageException e) {
                    log.error("Failed to persist token after renew: {}", e.getMessage(), e);
                }
                log.info("Token renewed successfully at {}", Instant.now());
                return true;
            }

            log.error("Failed to extract renewed token from response: {}", response);
            return false;

        } catch (Exception e) {
            log.error("Error renewing token: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Start periodic token renewal
     */
    private void startAutoRenewal() {
        if (running) {
            return;
        }

        running = true;
        long renewalIntervalMinutes = params.getTokenRenewalIntervalMinutes();

        log.info("Starting token renewal every {} minutes", renewalIntervalMinutes);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("Attempting to renew token... (scheduled)");
                boolean ok = renewToken();
                if (!ok) log.warn("Scheduled renewal failed");
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
     * Get the current token string
     */
    public String getCurrentToken() {
        TokenHolder holder = current.get();
        if (holder == null) return null;
        return holder.getToken();
    }

    /**
     * Insert or replace the current token holder and persist it to the configured TokenStore (if writable).
     */
    public void setTokenHolder(TokenHolder holder) throws TokenStorageException {
        if (holder == null || holder.getToken() == null || holder.getToken().isEmpty()) throw new IllegalArgumentException("holder must contain a non-empty token");
        current.set(holder);
        persistCurrentHolder();
    }

    private void persistCurrentHolder() throws TokenStorageException {
        if (tokenStore == null) throw new TokenStorageException("No token store configured to persist token");
        if (!tokenStore.isWritable()) throw new TokenStorageException("Token store is not writable: " + tokenStore.getClass().getName());
        try {
            String json = objMapper.writeValueAsString(current.get());
            tokenStore.write(json);
        } catch (Exception e) {
            throw new TokenStorageException("Failed to persist token: " + e.getMessage(), e);
        }
    }

    /**
     * Build server URL based on configuration
     */
    private String buildServerUrl() {
        String protocol = params.getRemotePort() == 443 ? "https" : "http";
        return protocol + "://" + params.getRemoteHost() + ":" + params.getRemotePort();
    }

    private TokenHolder parseTokenHolderFromApiResponse(String jsonResponse) {
        try {
            // Expecting structure: { "success": true, "reasonCode": 0, "data": { "expiresAt": "...Z", "token": "..." } }
            // naive extraction: find "data":{ ... }
            int dataIdx = jsonResponse.indexOf("\"data\"");
            if (dataIdx == -1) return null;
            int brace = jsonResponse.indexOf('{', dataIdx);
            if (brace == -1) return null;
            int end = jsonResponse.lastIndexOf('}');
            if (end == -1 || end <= brace) return null;
            String dataJson = jsonResponse.substring(brace, end + 1);
            // map to TokenHolder
            return objMapper.readValue(dataJson, TokenHolder.class);
        } catch (Exception e) {
            log.error("Failed to parse token holder from API response: {}", e.getMessage());
            return null;
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // support java.time types (Instant, LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());
        // ensure dates are serialized/deserialized as ISO-8601 strings, not numbers
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}

