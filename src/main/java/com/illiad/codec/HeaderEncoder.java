package com.illiad.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.socksx.SocksMessage;
import org.springframework.stereotype.Component;

/**
 * Encodes a client-side {@link SocksMessage} into a {@link ByteBuf}.
 */
@Component
@ChannelHandler.Sharable
public class HeaderEncoder extends MessageToByteEncoder<SocksMessage> {

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
        ctx.pipeline().remove(this);
    }

}
