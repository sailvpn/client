
package com.illiad.proxy.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.config.TokenMode;
import com.illiad.proxy.dto.Data;
import com.illiad.proxy.dto.TokenGenerateRequest;
import com.illiad.proxy.dto.TokenResponse;
import io.jsonwebtoken.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TokenManager {
    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);
    private final Params params;
    private final ObjectMapper objMapper = createObjectMapper();
    private final HttpClient client;
    private final TokenStore tokenStore;
    private volatile boolean running = false;
    private final AtomicReference<String> current = new AtomicReference<>();
    private volatile Disposable renewDisposable;

    @Autowired
    public TokenManager(Params params, HttpClient client) throws TokenStorageException {
        this.params = params;
        this.client = client;

        // tokenStore selection unchanged
        String configured = params.getJwtTokenFile();
        if (configured != null && !configured.isEmpty()) {
            this.tokenStore = new FileTokenStore(configured);
        } else {
            this.tokenStore = new FileTokenStore("./token.jwt");
        }

        // Load existing token holder from store if present (off main reactor threads)
        current.set(tokenStore.read());
    }


    public String getCurrentToken() {
        return current.get();
    }


    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Centralized POST to token/generate and parse into TokenHolder.
     * Ensures file writes happen on boundedElastic scheduler.
     */
    private Mono<Data> postGenerate(byte[] requestBytes) {
        String uri = "https://" + params.getRemoteHost() + ":" + params.getRemotePort() + "/api/auth/token/generate";

        return client
                .headers(headers -> headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON))
                .post()
                .uri(uri)
                .send(Mono.just(Unpooled.wrappedBuffer(requestBytes)))
                .responseSingle((response, byteBufMono) -> byteBufMono.asString(StandardCharsets.UTF_8)
                        .flatMap(body -> {
                            int code = response.status().code();
                            if (code >= 200 && code < 300) {
                                try {
                                    TokenResponse tr = objMapper.readValue(body, TokenResponse.class);
                                    return Mono.just(tr);
                                } catch (JsonProcessingException e) {
                                    return Mono.error(e);
                                }
                            } else {
                                return Mono.error(new RuntimeException("HTTP " + code + ": " + body));
                            }
                        })
                )
                // CRITICAL FIX: Shifts execution to background threads to break the Netty Event Loop deadlock
                .publishOn(Schedulers.boundedElastic())
                // Persist token to store on boundedElastic to avoid blocking reactor event loops
                .flatMap(tr -> Mono.fromCallable(() -> {
                    if (tokenStore != null) {
                        tokenStore.write(tr.getData().getToken());
                    }
                    return tr.getData();
                }));
    }


    /**
     * Initialize token management - acquire initial token and start renewal
     */
    public void initialize() throws JsonProcessingException {
        if (!Objects.equals(params.getCrypto(), "JWT")) {
            log.info("Token management disabled (crypto != JWT)");
            return;
        }

        TokenMode mode;
        try {
            mode = TokenMode.valueOf(params.getTokenMode().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid tokenMode: " + params.getTokenMode(), ex);
        }

        if (TokenMode.AUTO == mode) {
            log.info("Automatic token mode enabled (tokenMode=auto)");
            Instant expiresAt = getExpireEpoch();
            Instant now = Instant.now();

            if (now.isAfter(expiresAt)) {
                if (params.getUsername() == null || params.getUsername().isEmpty() ||
                        params.getPassword() == null || params.getPassword().isEmpty()) {
                    throw new IllegalStateException("Automatic token mode requires username/password when no valid token is available");
                }

                TokenGenerateRequest req = new TokenGenerateRequest();
                req.setUsername(params.getUsername());
                req.setPassword(params.getPassword());
                req.setExpirationMinutes(params.getExpireMins());
                byte[] requestBytes = objMapper.writeValueAsBytes(req);

                try {
                    // CRITICAL FIX: Add explicit scheduling off the main thread before blocking
                    Data data = postGenerate(requestBytes)
                            .subscribeOn(Schedulers.boundedElastic()) // Frees startup thread during connection setup
                            .publishOn(Schedulers.boundedElastic())   // Frees Netty Event Loops during body evaluation
                            .timeout(Duration.ofSeconds(10))
                            .block(); // Safe to block here now

                    if (data != null) {
                        current.set(data.getToken());
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to acquire initial token", e);
                }
            } else if (Duration.between(now, expiresAt).toMinutes() < params.getRenewInterval() * 5L) {
                // Proactively renew once at startup if close to expiry; do this asynchronously (don't block startup)
                TokenGenerateRequest req = new TokenGenerateRequest();
                req.setCurrentToken(current.get());
                req.setExpirationMinutes(params.getExpireMins());
                byte[] requestBytes;
                try {
                    requestBytes = objMapper.writeValueAsBytes(req);
                    postGenerate(requestBytes)
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe(
                                    data -> current.set(data.getToken()),
                                    err -> log.error("Error acquiring token: {}", err.getMessage(), err)
                            );
                } catch (JsonProcessingException e) {
                    log.error("Failed to build token renewal request", e);
                }
            }
            startAutoRenewal();
        } else {
            // MANUAL
            try {
                String stored = tokenStore != null ? tokenStore.read() : null;
                if (stored == null || stored.isEmpty()) {
                    throw new IllegalStateException(
                            "Manual token mode (tokenMode=manual) requires a token present in the configured token store/file. " +
                                    "Please create the token file with the proxy server token."
                    );
                }
                current.set(stored);
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
     * Start periodic token renewal using Reactor's Flux.interval and track the Disposable.
     */
    private void startAutoRenewal() {
        if (running) {
            return;
        }
        running = true;
        long renewInterval = params.getRenewInterval();
        log.info("Starting token renewal every {} minutes", renewInterval);

        // build bytes template for renew requests when needed
        renewDisposable = reactor.core.publisher.Flux.interval(Duration.ofMinutes(renewInterval), Duration.ofMinutes(renewInterval), Schedulers.parallel())
                .flatMap(tick -> {
                    if (Duration.between(Instant.now(), getExpireEpoch()).toMinutes() < params.getRenewInterval() * 5L) {
                        try {
                            TokenGenerateRequest req = new TokenGenerateRequest();
                            req.setCurrentToken(current.get());
                            req.setExpirationMinutes(params.getExpireMins());
                            byte[] requestBytes = objMapper.writeValueAsBytes(req);
                            return postGenerate(requestBytes)
                                    .doOnNext(data -> current.set(data.getToken()))
                                    .onErrorResume(e -> {
                                        log.error("Error renewing token: {}", e.getMessage(), e);
                                        return Mono.empty();
                                    });
                        } catch (JsonProcessingException e) {
                            log.error("Failed to serialize renewal request", e);
                            return Mono.empty();
                        }
                    } else {
                        return Mono.empty();
                    }
                })
                .subscribe(); // keep Disposable in renewDisposable
    }

    /**
     * Stop token renewal
     */
    public void shutdown() {
        running = false;
        if (renewDisposable != null && !renewDisposable.isDisposed()) {
            renewDisposable.dispose();
        }
    }

    private Instant getExpireEpoch() {
        Claims claims = parseJWT(current.get());
        if (claims != null) {
            // Standard 'exp' claim is retrieved as a Date object in JJWT
            Date expiration = claims.getExpiration();
            if (expiration != null) {
                return expiration.toInstant();
            }
        }
        return Instant.EPOCH; // treat as expired if we can't parse
    }

    private static Claims parseJWT(String jwtString) {
        if (jwtString != null) {
            try {
                // Standard JJWT 0.12+ approach to parse without signature verification
                int i = jwtString.lastIndexOf('.');
                if (i > 0) {
                    String withoutSignature = jwtString.substring(0, i + 1);
                    return Jwts.parser()
                            .unsecured()
                            .build()
                            .parseUnsecuredClaims(withoutSignature)
                            .getPayload();
                }
            } catch (Exception e) {
                // Log error or ignore if the token format is invalid
            }
        }
        return null;
    }

}

