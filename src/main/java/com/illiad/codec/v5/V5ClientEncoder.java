package com.illiad.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.socksx.v5.*;
import org.springframework.stereotype.Component;

/**
 * Encodes a client-side {@link Socks5CommandRequest} into a {@link ByteBuf}.
 * only Connect requests are expected here
 */
@Component
@Sharable
public class V5ClientEncoder extends MessageToByteEncoder<Socks5CommandRequest> {

    private final V5AddressEncoder v5AddressEncoder;

    public V5ClientEncoder(V5AddressEncoder v5AddressEncoder) {
        this.v5AddressEncoder = v5AddressEncoder;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks5CommandRequest request, ByteBuf out) {
        out.writeByte(request.version().byteValue());
        out.writeByte(request.type().byteValue());
        out.writeByte(0x00);

        final Socks5AddressType dstAddrType = request.dstAddrType();
        out.writeByte(dstAddrType.byteValue());
        v5AddressEncoder.encodeAddress(dstAddrType, request.dstAddr(), out);
        ByteBufUtil.writeShortBE(out, request.dstPort());

    }

}

