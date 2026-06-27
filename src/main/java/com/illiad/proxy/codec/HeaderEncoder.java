package com.illiad.proxy.codec;

import com.illiad.proxy.security.Secret;
import io.netty.buffer.ByteBuf;
import org.springframework.stereotype.Component;

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
        int tokenLen = secretBytes.length;
        // Packing byteBuf
        // frameLength = Fields after the first 2 bytes = 2 bytes (tokenLen) + 1 byte (type) + token length + pad length
        byteBuf.writeShort(3 + tokenLen + offset.length);
        // token length
        byteBuf.writeShort(tokenLen);
        // crypt type
        byteBuf.writeByte(secret.getCryptoTypeByte());
        // token
        byteBuf.writeBytes(secretBytes);
        // pad
        byteBuf.writeBytes(offset);
    }

}
