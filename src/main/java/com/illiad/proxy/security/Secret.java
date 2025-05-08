package com.illiad.proxy.security;


import java.security.NoSuchAlgorithmException;

public interface Secret {
    byte[] getSecret() throws Exception;

    Cryptos getCryptoType();

    Cryptos getCryptoType(byte b);

    byte getCryptoTypeByte();

    short getCryptoLength();

    boolean verify(byte[] secret) throws NoSuchAlgorithmException;

    byte[] offset();
}
