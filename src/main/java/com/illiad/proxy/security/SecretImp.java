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
    private final SecureRandom secureRandom = new SecureRandom();
    private final TokenManager tokenManager;

    public SecretImp(Params params, TokenManager tokenManager) {
        this.params = params;
        this.tokenManager = tokenManager;
    }

    @Override
    public byte[] getSecret() throws NoSuchAlgorithmException {
        // Check if crypto type is JWT
        Cryptos cryptoType = Cryptos.valueOf(params.getCrypto());

        if (Cryptos.JWT2 == cryptoType || Cryptos.JWT == cryptoType) {
            // Get current token from token manager (may be dynamically renewed)
            String currentToken = tokenManager.getCurrentToken();
            if (currentToken == null || currentToken.isEmpty()) {
                throw new IllegalStateException("Token is not configured. Please create the token file or configure credentials for auto mode.");
            }
            return currentToken.getBytes(StandardCharsets.UTF_8);
        } else {
            // Hash-based authentication
            MessageDigest digest = MessageDigest.getInstance(cryptoType.getValue());
            return digest.digest(params.getSecret().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public Cryptos getCryptoType() {
        return Cryptos.valueOf(params.getCrypto());
    }

    @Override
    public byte getCryptoTypeByte() {
        return Cryptos.valueOf(params.getCrypto()).getCode();
    }

    @Override
    public short getCryptoLength() {
        return Cryptos.valueOf(params.getCrypto()).getLength();
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
