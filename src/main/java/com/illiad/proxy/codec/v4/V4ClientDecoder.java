package com.illiad.proxy.codec.v4;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.util.NetUtil;

import java.util.List;

/**
 * Decodes a single {@link Socks4CommandResponse} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove this decoder later.  On failed decode, this decoder will discard the
 * received data, so that other handler closes the connection later.
 */
public class V4ClientDecoder extends ReplayingDecoder<V4ClientDecoder.State> {

    public V4ClientDecoder() {
        super(State.START);
        setSingleDecode(true);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        try {
            switch (state()) {
                case START: {
                    final int version = in.readUnsignedByte();
                    if (version != 0) {
                        throw new DecoderException("unsupported reply version: " + version + " (expected: 0)");
                    }

                    final Socks4CommandStatus status = Socks4CommandStatus.valueOf(in.readByte());
                    final int dstPort = ByteBufUtil.readUnsignedShortBE(in);
                    final String dstAddr = NetUtil.intToIpAddress(ByteBufUtil.readIntBE(in));

                    out.add(new DefaultSocks4CommandResponse(status, dstAddr, dstPort));
                    checkpoint(State.SUCCESS);
                }
                casCCESS: {
e                            int readableBytes = actualReadableBytes();
                    if (readableBytes > 0) {
                        out.add(in.readRetainedSlice(readableBytes));
                    }
                    ctx.pipeline().remove(this);
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
        Socks4CommandResponse m = new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED);
        if (cause instanceof DecoderException) {
            m.setDecoderResult(DecoderResult.failure(cause));
        } else {
            m.setDecoderResult(DecoderResult.failure(new DecoderException(cause)));
        }
        out.add(m);

        checkpoint(State.FAILURE);
    }

    enum State {
        START,
        SUCCESS,
        FAILURE
    }
}

