package com.illiad.proxy.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

/**
 * Decodes a single {@link Socks5CommandRequest} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later.  On failed decode, this decoder will
 * discard the received data, so that other handler closes the connection later.
 */

public class V5CmdReqDecoder extends ReplayingDecoder<V5CmdReqDecoder.State> {

    private final V5AddressDecoder v5AddressDecoder;

    public V5CmdReqDecoder(V5AddressDecoder v5AddressDecoder) {
        super(State.INIT);
        this.v5AddressDecoder = v5AddressDecoder;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        try {
            switch (state()) {
                case INIT: {
                    final byte version = in.readByte();
                    if (version != SocksVersion.SOCKS5.byteValue()) {
                        throw new DecoderException(
                                "unsupported version: " + version + " (expected: " + SocksVersion.SOCKS5.byteValue() + ')');
                    }

                    final Socks5CommandType type = Socks5CommandType.valueOf(in.readByte());
                    in.skipBytes(1); // RSV
                    final Socks5AddressType dstAddrType = Socks5AddressType.valueOf(in.readByte());
                    final String dstAddr = v5AddressDecoder.decodeAddress(dstAddrType, in);
                    final int dstPort = ByteBufUtil.readUnsignedShortBE(in);

                    out.add(new DefaultSocks5CommandRequest(type, dstAddrType, dstAddr, dstPort));
                    checkpoint(State.SUCCESS);
                }
                case SUCCESS: {
                    int readableBytes = actualReadableBytes();
                    if (readableBytes > 0) {
                        out.add(in.readRetainedSlice(readableBytes));
                    }
                    ctx.pipeline().remove(this);
                    break;
                }
                case FAILURE: {
                    in.skipBytes(actualReadableBytes());
                    ctx.pipeline().remove(this);
                    break;
                }
            }
        } catch (Exception e) {
            fail(out, e);
        }
    }

    private void fail(List<Object> out, Exception cause) {
        checkpoint(State.FAILURE);

        Socks5Message m = new DefaultSocks5CommandRequest(
                Socks5CommandType.CONNECT, Socks5AddressType.IPv4, "0.0.0.0", 1);
        if (cause instanceof DecoderException) {
            m.setDecoderResult(DecoderResult.failure(cause));
        } else {
            m.setDecoderResult(DecoderResult.failure(new DecoderException(cause)));
        }
        out.add(m);
    }

    enum State {
        INIT,
        SUCCESS,
        FAILURE
    }
}
