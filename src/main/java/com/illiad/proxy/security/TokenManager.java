package com.illiad.proxy.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.config.TokenMode;
import com.illiad.proxy.dto.TokenGenerateRequest;
import io.netty.buffer.Unpooled;
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
    private final HttpClient client;
    private final TokenStore tokenStore;
    private volatile boolean running = false;
    // Holds the last known token and its expiresAt
    private final AtomicReference<TokenHolder> current = new AtomicReference<>();

    @Autowired
    public TokenManager(Params params, HttpClient client) throws TokenStorageException, JsonProcessingException {
        this.params = params;
        this.client = client;
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
    TokenManager(Params params, HttpClient client, TokenStore store) {
        this.params = params;
        this.client = client;
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
    public void initialize() throws JsonProcessingException {
        if (!Objects.equals(params.getCrypto(), "JWT")) {
            log.info("Token management disabled (crypto != JWT)");
            return;
        }

        if (TokenMode.AUTO == TokenMode.valueOf(params.getTokenMode())) {
            log.info("Automatic token mode enabled (tokenMode=auto)");

            if (current.get().isExpired()) {
                // If no token present or expired => try to acquire via username/password
                if (params.getUsername() == null || params.getUsername().isEmpty() ||
                        params.getPassword() == null || params.getPassword().isEmpty()) {
                    throw new IllegalStateException("Automatic token mode requires username/password when no valid token is available");
                }

                // Build JSON request body
                TokenGenerateRequest req = new TokenGenerateRequest();
                req.setUsername(params.getUsername());
                req.setPassword(params.getPassword());
                req.setExpirationMinutes(params.getExpireMins());
                byte[] requestBytes = objMapper.writeValueAsBytes(req);

                client.post()
                        .uri("https://" + params.getRemoteHost() + ":" + params.getRemotePort() + "/api/auth/token/generate")
                        .send(Mono.just(Unpooled.wrappedBuffer(requestBytes)))
                        .responseSingle((response, byteBufMono) -> {
                            if (response.status().code() >= 200 && response.status().code() < 300) {
                                return byteBufMono.asString(StandardCharsets.UTF_8);
                            } else {
                                return byteBufMono.asString(StandardCharsets.UTF_8)
                                        .flatMap(body -> Mono.error(new RuntimeException("HTTP request failed with status " + response.status().code() + ": " + body)));
                            }
                        }).subscribe(s -> {
                            // Parse response into TokenHolder (expects data.expiresAt and data.token)
                            try {
                                tokenStore.write(s);
                                TokenHolder holder = objMapper.readValue(s, TokenHolder.class);
                                current.set(Objects.requireNonNullElseGet(holder, TokenManager::emptyHolder));
                            } catch (TokenStorageException | JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }, error -> {
                            log.error("Error acquiring token: {}", error.getMessage(), error);
                        });

                // Token not expired. Check remaining validity.
            } else if (Duration.between(Instant.now(), current.get().getExpiresAt()).toMinutes() < params.getRenewInterval() * 5L) {
                // Build JSON request body

                TokenGenerateRequest req = new TokenGenerateRequest();
                req.setCurrentToken(current.get().getToken());
                req.setExpirationMinutes(params.getExpireMins());
                byte[] requestBytes = objMapper.writeValueAsBytes(req);

                client.post()
                        .uri("https://" + params.getRemoteHost() + ":" + params.getRemotePort() + "/api/auth/token/generate")
                        .send(Mono.just(Unpooled.wrappedBuffer(requestBytes)))
                        .responseSingle((response, byteBufMono) -> {
                            if (response.status().code() >= 200 && response.status().code() < 300) {
                                return byteBufMono.asString(StandardCharsets.UTF_8);
                            } else {
                                return byteBufMono.asString(StandardCharsets.UTF_8)
                                        .flatMap(body -> Mono.error(new RuntimeException("HTTP request failed with status " + response.status().code() + ": " + body)));
                            }
                        }).subscribe(s -> {
                            // Parse response into TokenHolder (expects data.expiresAt and data.token)
                            try {
                                tokenStore.write(s);
                                TokenHolder holder = objMapper.readValue(s, TokenHolder.class);
                                current.set(Objects.requireNonNullElseGet(holder, TokenManager::emptyHolder));
                            } catch (TokenStorageException | JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }, error -> {
                            log.error("Error acquiring token: {}", error.getMessage(), error);
                        });
            }


            // start renewal if enabled
            startAutoRenewal();

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
     * Start periodic token renewal
     */
    private void startAutoRenewal() {
        if (running) {
            return;
        }

        running = true;
        long renewInterval = params.getRenewInterval();

        log.info("Starting token renewal every {} minutes", renewInterval);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("Attempting to renew token... (scheduled)");
                TokenGenerateRequest req = new TokenGenerateRequest();
                req.setCurrentToken(current.get().getToken());
                req.setExpirationMinutes(params.getExpireMins());
                byte[] requestBytes = objMapper.writeValueAsBytes(req);

                client.post()
                        .uri("https://" + params.getRemoteHost() + ":" + params.getRemotePort() + "/api/auth/token/generate")
                        .send(Mono.just(Unpooled.wrappedBuffer(requestBytes)))
                        .responseSingle((response, byteBufMono) -> {
                            if (response.status().code() >= 200 && response.status().code() < 300) {
                                return byteBufMono.asString(StandardCharsets.UTF_8);
                            } else {
                                return byteBufMono.asString(StandardCharsets.UTF_8)
                                        .flatMap(body -> Mono.error(new RuntimeException("HTTP request failed with status " + response.status().code() + ": " + body)));
                            }
                        }).subscribe(s -> {
                            // Parse response into TokenHolder (expects data.expiresAt and data.token)
                            try {
                                tokenStore.write(s);
                                TokenHolder holder = objMapper.readValue(s, TokenHolder.class);
                                current.set(Objects.requireNonNullElseGet(holder, TokenManager::emptyHolder));
                            } catch (TokenStorageException | JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }, error -> {
                            log.error("Error acquiring token: {}", error.getMessage(), error);
                        });
            } catch (Exception e) {
                log.error("Error in token renewal task: {}", e.getMessage(), e);
            }
        }, renewInterval, renewInterval, TimeUnit.MINUTES);
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


    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // support java.time types (Instant, LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());
        // ensure dates are serialized/deserialized as ISO-8601 strings, not numbers
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}

