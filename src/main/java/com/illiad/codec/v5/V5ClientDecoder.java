package com.illiad.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder.State;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

/**
 * Decodes a single {@link Socks5CommandResponse} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later.  On failed decode, this decoder will
 * discard the received data, so that other handler closes the connection later.
 */
public class V5ClientDecoder extends ReplayingDecoder<State> {

    @Autowired
    private V5AddressDecoder v5AddressDecoder;

    public V5ClientDecoder() {
        super(State.INIT);
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
                    final Socks5CommandStatus status = Socks5CommandStatus.valueOf(in.readByte());
                    in.skipBytes(1); // Reserved
                    final Socks5AddressType addrType = Socks5AddressType.valueOf(in.readByte());
                    final String addr = v5AddressDecoder.decodeAddress(addrType, in);
                    final int port = ByteBufUtil.readUnsignedShortBE(in);

                    out.add(new DefaultSocks5CommandResponse(status, addrType, addr, port));
                    checkpoint(io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder.State.SUCCESS);
                }
                case SUCCESS: {
                    int readableBytes = actualReadableBytes();
                    if (readableBytes > 0) {
                        out.add(in.readRetainedSlice(readableBytes));
                    }
                    break;
                }
                case FAILURE: {
                    in.skipBytes(actualReadableBytes());
                    break;
                }
            }
        } catch (Exception e) {
            fail(out, e);
        }
    }

    private void fail(List<Object> out, Exception cause) {
        if (!(cause instanceof DecoderException)) {
            cause = new DecoderException(cause);
        }

        checkpoint(io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder.State.FAILURE);

        Socks5Message m = new DefaultSocks5CommandResponse(
                Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4, null, 0);
        m.setDecoderResult(DecoderResult.failure(cause));
        out.add(m);
    }
}
