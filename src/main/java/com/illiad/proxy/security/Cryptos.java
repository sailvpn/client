package com.illiad.proxy.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum Cryptos {
    JWT2("JWT2", (byte) 0x81, (short) 0),
    JWT("JWT", (byte) 0x82, (short) 0),
    SHA_256("SHA-256", (byte) 0x83, (short) 32);

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