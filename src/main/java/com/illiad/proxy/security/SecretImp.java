package com.illiad.proxy.security;

import com.illiad.proxy.config.Params;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

@Component
public class SecretImp implements Secret {
    private final Params params;
    private final CryptoByte cryptoByte;
    private final SecureRandom secureRandom = new SecureRandom();
    private final TokenManager tokenManager;

    public SecretImp(Params params, CryptoByte cryptoByte, TokenManager tokenManager) {
        this.params = params;
        this.cryptoByte = cryptoByte;
        this.tokenManager = tokenManager;
    }

    @Override
    public byte[] getSecret() throws NoSuchAlgorithmException {
        // Check if crypto type is JWT
        if (getCryptoType() == Cryptos.JWT) {
            // Get current token from token manager (may be dynamically renewed)
            String currentToken = tokenManager.getCurrentToken();
            if (currentToken == null || currentToken.isEmpty()) {
                throw new IllegalStateException("Token is not configured. Please create the token file or configure credentials for auto mode.");
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
        // generate random ran bytes of length params.min..params.max
        int offsetLen = secureRandom.nextInt(params.getMax()) + params.getMin();
        byte[] offsetBytes = new byte[offsetLen];
        secureRandom.nextBytes(offsetBytes);
        return offsetBytes;
    }

}
