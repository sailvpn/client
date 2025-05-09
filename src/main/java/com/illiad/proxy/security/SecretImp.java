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

    public SecretImp(Params params, CryptoByte cryptoByte) {
        this.params = params;
        this.cryptoByte = cryptoByte;
        this.random = new Random();
    }

    @Override
    public byte[] getSecret() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(params.getCrypto());
        return digest.digest(params.getSecret().getBytes(StandardCharsets.UTF_8));
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
