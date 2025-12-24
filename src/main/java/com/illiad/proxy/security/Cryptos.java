package com.illiad.proxy.security;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

public enum Cryptos {
    JWT("JWT", (byte) 0x07),
    SHA_224("SHA-224", (byte) 0x10),
    SHA_256("SHA-256", (byte) 0x20),
    SHA_384("SHA-384", (byte) 0x30),
    SHA_512("SHA-512", (byte) 0x40),
    SHA_512_224("SHA-512/224", (byte) 0x50),
    SHA_512_256("SHA-512/256", (byte) 0x60),
    HMAC_SHA224("HmacSHA224", (byte) 0x70),
    HMAC_SHA256("HmacSHA256", (byte) 0x80),
    HMAC_SHA384("HmacSHA384", (byte) 0x90),
    HMAC_SHA512("HmacSHA512", (byte) 0xA0),
    SHA224_WITH_RSA("SHA224withRSA", (byte) 0xB0),
    SHA256_WITH_RSA("SHA256withRSA", (byte) 0xC0),
    SHA384_WITH_RSA("SHA384withRSA", (byte) 0xD0),
    SHA512_WITH_RSA("SHA512withRSA", (byte) 0xE0),
    SHA224_WITH_DSA("SHA224withDSA", (byte) 0xF0),
    SHA256_WITH_DSA("SHA256withDSA", (byte) 0x01),
    SHA384_WITH_DSA("SHA384withDSA", (byte) 0x11),
    SHA512_WITH_DSA("SHA512withDSA", (byte) 0x21),
    SHA224_WITH_ECDSA("SHA224withECDSA", (byte) 0x31),
    SHA256_WITH_ECDSA("SHA256withECDSA", (byte) 0x41),
    SHA384_WITH_ECDSA("SHA384withECDSA", (byte) 0x51),
    SHA512_WITH_ECDSA("SHA512withECDSA", (byte) 0x61),
    SHA3_224("SHA3-224", (byte) 0x71),
    SHA3_256("SHA3-256", (byte) 0x81),
    SHA3_384("SHA3-384", (byte) 0x91),
    SHA3_512("SHA3-512", (byte) 0xA1),
    HMAC_SHA3_224("HmacSHA3-224", (byte) 0xB1),
    HMAC_SHA3_256("HmacSHA3-256", (byte) 0xC1),
    HMAC_SHA3_384("HmacSHA3-384", (byte) 0xD1),
    HMAC_SHA3_512("HmacSHA3-512", (byte) 0xE1);

    private final String value;
    private final byte code;

    Cryptos(String value, byte code) {
        this.value = value;
        this.code = code;
    }

    public String getValue() {
        return value;
    }

    public byte getCode() {
        return code;
    }

    private static final Map<Byte, Cryptos> BY_CODE = new HashMap<>();

    static {
        for (Cryptos c : values()) {
            BY_CODE.put(c.code, c);
        }
    }

    public static Optional<Cryptos> fromCode(byte code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    @Override
    public String toString() {
        return value + " (0x" + String.format("%02X", Byte.toUnsignedInt(code)) + ")";
    }
}