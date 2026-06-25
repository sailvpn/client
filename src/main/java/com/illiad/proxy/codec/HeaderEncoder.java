package com.illiad.proxy.codec;

import com.illiad.proxy.security.Secret;
import io.netty.buffer.ByteBuf;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * illiad header frame:
 * - 2 bytes: frame length (unsigned short)
 * - 2 bytes: token length (unsigned short)
 * - 1 byte: crypto type
 * - token bytes: variable
 * - random bytes: variable
 */
@Component
public class HeaderEncoder {

    private final Secret secret;

    public HeaderEncoder(Secret secret) {
        this.secret = secret;
    }

    public void encodeHeader(ByteBuf byteBuf) {
        // get secret
        byte[] secretBytes;
        try {
            secretBytes = secret.getSecret();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        byte[] offset = secret.offset(); // Cryptographically secure random binary data

        int secretLength = secretBytes.length;
        // 2. Calculate Total Payload Length (Fields after the first 2 bytes)
        // 2 bytes (jwtLength) + 1 byte (typeInfo) + jwt length + random padding length
        int frameLength = 2 + 1 + secretLength + offset.length;

        // 3. Pack everything sequentially into the Netty ByteBuf
        byteBuf.writeShort(frameLength);
        byteBuf.writeShort(secretLength);
        byteBuf.writeByte(secret.getCryptoTypeByte());
        byteBuf.writeBytes(byteBuf);
        byteBuf.writeBytes(offset);
    }

}
