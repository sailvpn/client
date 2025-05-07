package com.illiad.proxy.security;

import org.springframework.stereotype.Component;

@Component
public class CryptoByte {

    public byte toByte(Cryptos c) {
        switch (c) {
            case SHA_224:
                return (byte) 0x10;
            case SHA_256:
                return (byte) 0x20;
            case SHA_384:
                return (byte) 0x30;
            case SHA_512:
                return (byte) 0x40;
            case SHA_512_224:
                return (byte) 0x50;
            case SHA_512_256:
                return (byte) 0x60;
            case HMAC_SHA224:
                return (byte) 0x70;
            case HMAC_SHA256:
                return (byte) 0x80;
            case HMAC_SHA384:
                return (byte) 0x90;
            case HMAC_SHA512:
                return (byte) 0xA0;
            case SHA224_WITH_RSA:
                return (byte) 0xB0;
            case SHA256_WITH_RSA:
                return (byte) 0xC0;
            case SHA384_WITH_RSA:
                return (byte) 0xD0;
            case SHA512_WITH_RSA:
                return (byte) 0xE0;
            case SHA224_WITH_DSA:
                return (byte) 0xF0;
            case SHA256_WITH_DSA:
                return (byte) 0x01;
            case SHA384_WITH_DSA:
                return (byte) 0x11;
            case SHA512_WITH_DSA:
                return (byte) 0x21;
            case SHA224_WITH_ECDSA:
                return (byte) 0x31;
            case SHA256_WITH_ECDSA:
                return (byte) 0x41;
            case SHA384_WITH_ECDSA:
                return (byte) 0x51;
            case SHA512_WITH_ECDSA:
                return (byte) 0x61;
            case SHA3_224:
                return (byte) 0x71;
            case SHA3_256:
                return (byte) 0x81;
            case SHA3_384:
                return (byte) 0x91;
            case SHA3_512:
                return (byte) 0xA1;
            case HMAC_SHA3_224:
                return (byte) 0xB1;
            case HMAC_SHA3_256:
                return (byte) 0xC1;
            case HMAC_SHA3_384:
                return (byte) 0xD1;
            case HMAC_SHA3_512:
                return (byte) 0xE1;
            default:
                throw new IllegalArgumentException("Unknown Cryptos standard: " + c);
        }
    }

    public Cryptos toCrypto(byte b) {
        switch (b) {
            case (byte) 0x10:
                return Cryptos.SHA_224;
            case (byte) 0x20:
                return Cryptos.SHA_256;
            case (byte) 0x30:
                return Cryptos.SHA_384;
            case (byte) 0x40:
                return Cryptos.SHA_512;
            case (byte) 0x50:
                return Cryptos.SHA_512_224;
            case (byte) 0x60:
                return Cryptos.SHA_512_256;
            case (byte) 0x70:
                return Cryptos.HMAC_SHA224;
            case (byte) 0x80:
                return Cryptos.HMAC_SHA256;
            case (byte) 0x90:
                return Cryptos.HMAC_SHA384;
            case (byte) 0xA0:
                return Cryptos.HMAC_SHA512;
            case (byte) 0xB0:
                return Cryptos.SHA224_WITH_RSA;
            case (byte) 0xC0:
                return Cryptos.SHA256_WITH_RSA;
            case (byte) 0xD0:
                return Cryptos.SHA384_WITH_RSA;
            case (byte) 0xE0:
                return Cryptos.SHA512_WITH_RSA;
            case (byte) 0xF0:
                return Cryptos.SHA224_WITH_DSA;
            case (byte) 0x01:
                return Cryptos.SHA256_WITH_DSA;
            case (byte) 0x11:
                return Cryptos.SHA384_WITH_DSA;
            case (byte) 0x21:
                return Cryptos.SHA512_WITH_DSA;
            case (byte) 0x31:
                return Cryptos.SHA224_WITH_ECDSA;
            case (byte) 0x41:
                return Cryptos.SHA256_WITH_ECDSA;
            case (byte) 0x51:
                return Cryptos.SHA384_WITH_ECDSA;
            case (byte) 0x61:
                return Cryptos.SHA512_WITH_ECDSA;
            case (byte) 0x71:
                return Cryptos.SHA3_224;
            case (byte) 0x81:
                return Cryptos.SHA3_256;
            case (byte) 0x91:
                return Cryptos.SHA3_384;
            case (byte) 0xA1:
                return Cryptos.SHA3_512;
            case (byte) 0xB1:
                return Cryptos.HMAC_SHA3_224;
            case (byte) 0xC1:
                return Cryptos.HMAC_SHA3_256;
            case (byte) 0xD1:
                return Cryptos.HMAC_SHA3_384;
            case (byte) 0xE1:
                return Cryptos.HMAC_SHA3_512;
            default:
                throw new IllegalArgumentException("Unknown byte value: " + b);
        }
    }

    public short byteLength(Cryptos c) {
        switch (c) {
            case SHA_224:
            case HMAC_SHA224:
            case SHA3_224:
            case HMAC_SHA3_224:
                return 28; // 224 bits = 28 bytes

            case SHA_256:
            case HMAC_SHA256:
            case SHA3_256:
            case HMAC_SHA3_256:
                return 32; // 256 bits = 32 bytes

            case SHA_384:
            case HMAC_SHA384:
            case SHA3_384:
            case HMAC_SHA3_384:
                return 48; // 384 bits = 48 bytes

            case SHA_512:
            case HMAC_SHA512:
            case SHA3_512:
            case HMAC_SHA3_512:
                return 64; // 512 bits = 64 bytes

            case SHA_512_224:
                return 28; // 224 bits = 28 bytes

            case SHA_512_256:
                return 32; // 256 bits = 32 bytes

            case SHA224_WITH_RSA:
            case SHA224_WITH_DSA:
            case SHA224_WITH_ECDSA:
            case SHA256_WITH_RSA:
            case SHA256_WITH_DSA:
            case SHA256_WITH_ECDSA:
            case SHA384_WITH_RSA:
            case SHA384_WITH_DSA:
            case SHA384_WITH_ECDSA:
            case SHA512_WITH_RSA:
            case SHA512_WITH_DSA:
            case SHA512_WITH_ECDSA:
                return 0; // return 0 for variable length signatures

            default:
                throw new IllegalArgumentException("Unknown Cryptos standard: " + c);
        }
    }
}