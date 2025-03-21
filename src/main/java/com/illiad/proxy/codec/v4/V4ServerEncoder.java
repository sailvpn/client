package com.illiad.proxy.codec.v4;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.socksx.v4.Socks4CommandResponse;
import io.netty.util.NetUtil;
import org.springframework.stereotype.Component;

/**
 * Encodes a {@link Socks4CommandResponse} into a {@link ByteBuf}.
 */

@Component
@Sharable
public final class V4ServerEncoder extends MessageToByteEncoder<Socks4CommandResponse> {

    private static final byte[] IPv4_HOSTNAME_ZEROED = {0x00, 0x00, 0x00, 0x00};

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks4CommandResponse msg, ByteBuf out) {
        out.writeByte(0);
        out.writeByte(msg.status().byteValue());
        ByteBufUtil.writeShortBE(out, msg.dstPort());
        out.writeBytes(msg.dstAddr() == null ? IPv4_HOSTNAME_ZEROED
                : NetUtil.createByteArrayFromIpAddressString(msg.dstAddr()));
    }
}