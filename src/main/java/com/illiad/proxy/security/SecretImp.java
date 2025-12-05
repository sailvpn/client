package com.illiad.proxy.security;

import com.illiad.proxy.config.Params;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

@Component
public class SecretImp implements Secret {
    private final Params params;
    private final CryptoByte cryptoByte;
    private final Random random;
    private final JwtTokenManager tokenManager;

    public SecretImp(Params params, CryptoByte cryptoByte, JwtTokenManager tokenManager) {
        this.params = params;
        this.cryptoByte = cryptoByte;
        this.random = new Random();
        this.tokenManager = tokenManager;
    }

    @Override
    public byte[] getSecret() throws NoSuchAlgorithmException {
        // Check if crypto type is JWT
        if (getCryptoType() == Cryptos.JWT) {
            // Get current token from token manager (may be dynamically renewed)
            String currentToken = tokenManager.getCurrentToken();
            if (currentToken == null || currentToken.isEmpty()) {
                throw new IllegalStateException("JWT token is not configured. Please set jwtToken in application.properties or provide username/password.");
            }
            return currentToken.getBytes(StandardCharsets.UTF_8);
        } else {
            // Hash-based authentication
            MessageDigest digest = MessageDigest.getInstance(Cryptos.valueOf(params.getCrypto()).getValue());
            return digest.digest(params.getSecret().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public Cryptos getCryptoType() {
        return Cryptos.valueOf(params.getCrypto());
    }

    @Override
    public byte getCryptoTypeByte() {
        return this.cryptoByte.toByte(Cryptos.valueOf(params.getCrypto()));
    }

    @Override
    public short getCryptoLength() {
        return this.cryptoByte.byteLength(Cryptos.valueOf(params.getCrypto()));
    }

    @Override
    public byte[] offset() {
        int length = random.nextInt(params.getMax() - params.getMin()) + params.getMin();
        byte[] byteArray = new byte[length];
        random.nextBytes(byteArray);
        return byteArray;
    }

}
