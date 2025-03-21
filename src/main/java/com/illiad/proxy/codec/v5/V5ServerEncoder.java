package com.illiad.proxy.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.util.internal.StringUtil;
import org.springframework.stereotype.Component;

/**
 * Encodes a server-side {@link Socks5Message} into a {@link ByteBuf}.
 */
@Component
@Sharable
public class V5ServerEncoder extends MessageToByteEncoder<Socks5Message> {
    private final V5AddressEncoder v5addressEncoder;

    public V5ServerEncoder(V5AddressEncoder v5addressEncoder) {
        this.v5addressEncoder = v5addressEncoder;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks5Message msg, ByteBuf out) {
        if (msg instanceof Socks5InitialResponse) {
            encodeAuthMethodResponse((Socks5InitialResponse) msg, out);
        } else if (msg instanceof Socks5PasswordAuthResponse) {
            encodePasswordAuthResponse((Socks5PasswordAuthResponse) msg, out);
        } else if (msg instanceof Socks5CommandResponse) {
            encodeCommandResponse((Socks5CommandResponse) msg, out);
        } else {
            throw new EncoderException("unsupported message type: " + StringUtil.simpleClassName(msg));
        }
    }

    private static void encodeAuthMethodResponse(Socks5InitialResponse msg, ByteBuf out) {
        out.writeByte(msg.version().byteValue());
        out.writeByte(msg.authMethod().byteValue());
    }

    private static void encodePasswordAuthResponse(Socks5PasswordAuthResponse msg, ByteBuf out) {
        out.writeByte(0x01);
        out.writeByte(msg.status().byteValue());
    }

    private void encodeCommandResponse(Socks5CommandResponse msg, ByteBuf out) {
        out.writeByte(msg.version().byteValue());
        out.writeByte(msg.status().byteValue());
        out.writeByte(0x00);

        final Socks5AddressType bndAddrType = msg.bndAddrType();
        out.writeByte(bndAddrType.byteValue());
        v5addressEncoder.encodeAddress(bndAddrType, msg.bndAddr(), out);

        ByteBufUtil.writeShortBE(out, msg.bndPort());
    }
}
