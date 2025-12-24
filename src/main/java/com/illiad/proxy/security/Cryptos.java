package com.illiad.proxy.security;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

public enum Cryptos {
    JWT("JWT", (byte) 0x07, (short) 0),
    SHA_224("SHA-224", (byte) 0x10, (short) 28),
    SHA_256("SHA-256", (byte) 0x20, (short) 32),
    SHA_384("SHA-384", (byte) 0x30, (short) 48),
    SHA_512("SHA-512", (byte) 0x40, (short) 64),
    SHA_512_224("SHA-512/224", (byte) 0x50, (short) 28),
    SHA_512_256("SHA-512/256", (byte) 0x60, (short) 32),
    HMAC_SHA224("HmacSHA224", (byte) 0x70, (short) 28),
    HMAC_SHA256("HmacSHA256", (byte) 0x80, (short) 32),
    HMAC_SHA384("HmacSHA384", (byte) 0x90, (short) 48),
    HMAC_SHA512("HmacSHA512", (byte) 0xA0, (short) 64),
    SHA224_WITH_RSA("SHA224withRSA", (byte) 0xB0, (short) 0),
    SHA256_WITH_RSA("SHA256withRSA", (byte) 0xC0, (short) 0),
    SHA384_WITH_RSA("SHA384withRSA", (byte) 0xD0, (short) 0),
    SHA512_WITH_RSA("SHA512withRSA", (byte) 0xE0, (short) 0),
    SHA224_WITH_DSA("SHA224withDSA", (byte) 0xF0, (short) 0),
    SHA256_WITH_DSA("SHA256withDSA", (byte) 0x01, (short) 0),
    SHA384_WITH_DSA("SHA384withDSA", (byte) 0x11, (short) 0),
    SHA512_WITH_DSA("SHA512withDSA", (byte) 0x21, (short) 0),
    SHA224_WITH_ECDSA("SHA224withECDSA", (byte) 0x31, (short) 0),
    SHA256_WITH_ECDSA("SHA256withECDSA", (byte) 0x41, (short) 0),
    SHA384_WITH_ECDSA("SHA384withECDSA", (byte) 0x51, (short) 0),
    SHA512_WITH_ECDSA("SHA512withECDSA", (byte) 0x61, (short) 0),
    SHA3_224("SHA3-224", (byte) 0x71, (short) 28),
    SHA3_256("SHA3-256", (byte) 0x81, (short) 32),
    SHA3_384("SHA3-384", (byte) 0x91, (short) 48),
    SHA3_512("SHA3-512", (byte) 0xA1, (short) 64),
    HMAC_SHA3_224("HmacSHA3-224", (byte) 0xB1, (short) 28),
    HMAC_SHA3_256("HmacSHA3-256", (byte) 0xC1, (short) 32),
    HMAC_SHA3_384("HmacSHA3-384", (byte) 0xD1, (short) 48),
    HMAC_SHA3_512("HmacSHA3-512", (byte) 0xE1, (short) 64);

    private final String value;
    private final byte code;
    private final short length;

    Cryptos(String value, byte code, short length) {
        this.value = value;
        this.code = code;
        this.length = length;
    }

    public String getValue() {
        return value;
    }

    public byte getCode() {
        return code;
    }

    public short getLength() {
        return length;
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
        return value + " (0x" + String.format("%02X", Byte.toUnsignedInt(code)) + ", len=" + length + ")";
    }
}