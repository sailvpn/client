package com.illiad.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.socksx.v5.Socks5Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Encodes a client-side {@link Socks5Message} into a {@link ByteBuf}.
 */
@Component
@ChannelHandler.Sharable
public class IlliadSecretEncoder extends MessageToByteEncoder<Socks5Message> {

    @Autowired
    HeaderGen headerGen;

    @Autowired
    SecretGen secretGen;

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Socks5Message socks5Message, ByteBuf byteBuf) {
        // write header into byteBuf
        byte[] header = headerGen.header();
        byteBuf.writeByte(header.length);
        byteBuf.writeBytes(header);

        // write secret into byteBuf
        byte[] secret = secretGen.getSecret();
        int secretLength = secret.length;
        if (secretLength < 256) {
            byteBuf.writeByte(secretLength);
            byteBuf.writeByte(0);
        } else {
            byteBuf.writeByte(secretLength & 0xFF);
            byteBuf.writeByte((secretLength >> 8) & 0xFF);
        }
        byteBuf.writeBytes(secret);
    }

}
