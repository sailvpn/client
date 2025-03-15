package com.illiad.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5Message;

import java.util.List;

/**
 * Decodes a single {@link Socks5InitialRequest} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later.  On failed decode, this decoder will
 * discard the received data, so that other handler closes the connection later.
 */
public class V5InitReqDecoder extends ReplayingDecoder<State> {
    public V5InitReqDecoder() {
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

                    final int authMethodCnt = in.readUnsignedByte();

                    final Socks5AuthMethod[] authMethods = new Socks5AuthMethod[authMethodCnt];
                    for (int i = 0; i < authMethodCnt; i++) {
                        authMethods[i] = Socks5AuthMethod.valueOf(in.readByte());
                    }

                    out.add(new DefaultSocks5InitialRequest(authMethods));
                    checkpoint(State.SUCCESS);
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
        checkpoint(State.FAILURE);

        Socks5Message m = new DefaultSocks5InitialRequest(Socks5AuthMethod.NO_AUTH);
        if (cause instanceof DecoderException) {
            m.setDecoderResult(DecoderResult.failure(cause));
        } else {
            m.setDecoderResult(DecoderResult.failure(new DecoderException(cause)));
        }
        out.add(m);
    }
}
