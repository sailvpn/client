package com.illiad.proxy.security;


import java.security.NoSuchAlgorithmException;

public interface Secret {
    byte[] getSecret() throws Exception;

    Cryptos getCryptoType();

    byte getCryptoTypeByte();

    short getCryptoLength();

    byte[] offset();
}
