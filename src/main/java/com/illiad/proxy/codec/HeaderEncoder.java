package com.illiad.proxy.codec;

import com.illiad.proxy.security.Secret;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.socksx.SocksMessage;
import org.springframework.stereotype.Component;

/**
 * Encodes a client-side illiad Header into a {@link ByteBuf}.
 * an illiad header is a byte array of variable lenght, followed by CRLF.
 * the first 2 bytes is the length of the header. the next byte is the crypto type. then comes the signature, and a random offset.
 * if the crypto type indicates that the encryption return a fixed-length signature, the length field contains the whole length(signature + offset).
 * if the crypto type indicates that the encryption return a variable-length signature, the length field contain the length of the signature only.
 */
@Component
@ChannelHandler.Sharable
public class HeaderEncoder extends MessageToByteEncoder<SocksMessage> {

    private final static byte[] CRLF = new byte[]{0x0D, 0x0A};
    private final Secret secret;

    public HeaderEncoder(Secret secret) {
        this.secret = secret;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, SocksMessage socksMessage, ByteBuf byteBuf) {

        // get secret
        byte[] secretBytes;
        try {
            secretBytes = secret.getSecret();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        byte[] offset = secret.offset();
        short signLength = secret.getCryptoLength();
        // check if the crypto type is fixed length
        if (signLength > 0) {
            // if the crypto type indicates that the encryption return a fixed-length signature, the length field contains the whole length(signature + offset).
            byteBuf.writeShort((short) (signLength + offset.length) & 0xFFFF);
        } else {
            // if the crypto type indicates that the encryption return a variable-length signature, the length field contain the length of the signature only.
            byteBuf.writeShort((short) (secretBytes.length) & 0xFFFF);
        }
        // write crypto type, signature, and offset into ByteBuffer
        byteBuf.writeByte(secret.getCryptoTypeByte());
        byteBuf.writeBytes(secretBytes);
        byteBuf.writeBytes(offset);
        byteBuf.writeBytes(CRLF);
        ctx.writeAndFlush(socksMessage);
    }


}
