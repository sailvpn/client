package com.illiad.proxy.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.socksx.SocksMessage;
import org.springframework.stereotype.Component;

/**
 * Encodes a client-side illiad Header into a {@link ByteBuf}.
 * an illiad header consists by a variable offset, followed by CRLF, and a variable secret, followed by CRLF.
 * the 1 byte ahead of the offset is the length of the offset, and the 2 bytes ahead of the secret is the length of the secret.
 * this encoder is used to encode the header into a {@link ByteBuf} before sending it to the remote server.
 */
@Component
@ChannelHandler.Sharable
public class HeaderEncoder extends MessageToByteEncoder<SocksMessage> {

    private final static byte[] CRLF = new byte[]{0x0D, 0x0A};
    private final Header header;

    public HeaderEncoder(Header header) {
        this.header = header;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, SocksMessage socksMessage, ByteBuf byteBuf) {
        // write offset into byteBuf
        byte[] offset = header.offset();
        byteBuf.writeByte(offset.length);
        byteBuf.writeBytes(offset);
        byteBuf.writeBytes(CRLF);

        // write secret into byteBuf
        byte[] secret = header.getSecret().getSecret();
        int length = secret.length;
        if (length < 256) {
            byteBuf.writeByte(length);
            byteBuf.writeByte(0);
        } else {
            byteBuf.writeByte(length & 0xFF);
            byteBuf.writeByte((length >> 8) & 0xFF);
        }
        byteBuf.writeBytes(secret);
        byteBuf.writeBytes(CRLF);
    }

}
