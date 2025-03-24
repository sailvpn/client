package com.illiad.proxy.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V5InitReqDecoderTest {

    private V5InitReqDecoder decoder;
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        decoder = new V5InitReqDecoder();
        ctx = mock(ChannelHandlerContext.class);
    }

    @Test
    void testDecodeValidRequest() throws Exception {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(0x05); // SOCKS5 version
        in.writeByte(0x01); // 1 auth method
        in.writeByte(0x00); // NO_AUTH method

        List<Object> out = new java.util.ArrayList<>();
        decoder.decode(ctx, in, out);

        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof DefaultSocks5InitialRequest);
        DefaultSocks5InitialRequest request = (DefaultSocks5InitialRequest) out.get(0);
        assertEquals(Socks5AuthMethod.NO_AUTH, request.authMethods().get(0), "Auth methods");
    }

    @Test
    void testDecodeUnsupportedVersion() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(0x04); // Unsupported version

        List<Object> out = new java.util.ArrayList<>();
        assertThrows(DecoderException.class, () -> decoder.decode(ctx, in, out));
    }

    @Test
    void testDecodeFailure() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(0x05); // SOCKS5 version
        in.writeByte(0x01); // 1 auth method
        // Missing auth method byte

        List<Object> out = new java.util.ArrayList<>();
        decoder.decode(ctx, in, out);

        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof DefaultSocks5InitialRequest);
        DefaultSocks5InitialRequest request = (DefaultSocks5InitialRequest) out.get(0);
        assertTrue(request.decoderResult().isFailure());
    }
}