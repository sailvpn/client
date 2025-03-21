package com.illiad.proxy.codec.v4;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.util.NetUtil;
import org.springframework.stereotype.Component;

/**
 * Encodes a {@link Socks4CommandRequest} into a {@link ByteBuf}.
 */
@Component
@Sharable
public final class V4ClientEncoder extends MessageToByteEncoder<Socks4CommandRequest> {

    private static final byte[] IPv4_DOMAIN_MARKER = {0x00, 0x00, 0x00, 0x01};

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks4CommandRequest msg, ByteBuf out) throws Exception {
        out.writeByte(msg.version().byteValue());
        out.writeByte(msg.type().byteValue());
        ByteBufUtil.writeShortBE(out, msg.dstPort());
        if (NetUtil.isValidIpV4Address(msg.dstAddr())) {
            out.writeBytes(NetUtil.createByteArrayFromIpAddressString(msg.dstAddr()));
            ByteBufUtil.writeAscii(out, msg.userId());
            out.writeByte(0);
        } else {
            out.writeBytes(IPv4_DOMAIN_MARKER);
            ByteBufUtil.writeAscii(out, msg.userId());
            out.writeByte(0);
            ByteBufUtil.writeAscii(out, msg.dstAddr());
            out.writeByte(0);
        }
        ctx.pipeline().remove(this);
    }
}
