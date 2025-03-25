package com.illiad.proxy.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V5CmdReqDecoderTest {

    private V5CmdReqDecoder decoder;
    private ChannelHandlerContext ctx;
    private V5AddressDecoder v5AddressDecoder;

    @BeforeEach
    void setUp() {
        v5AddressDecoder = mock(V5AddressDecoder.class);
        decoder = new V5CmdReqDecoder();
        ctx = mock(ChannelHandlerContext.class);

        // Inject the mocked V5AddressDecoder into the decoder
        decoder.v5AddressDecoder = v5AddressDecoder;
    }

    @Test
    void testDecodeValidRequest() throws Exception {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(SocksVersion.SOCKS5.byteValue()); // version
        in.writeByte(Socks5CommandType.CONNECT.byteValue()); // command type
        in.writeByte(0x00); // RSV
        in.writeByte(Socks5AddressType.IPv4.byteValue()); // address type
        in.writeBytes(new byte[]{127, 0, 0, 1}); // address (127.0.0.1)
        in.writeShort(1080); // port

        when(v5AddressDecoder.decodeAddress(Socks5AddressType.IPv4, in)).thenReturn("127.0.0.1");

        List<Object> out = new java.util.ArrayList<>();
        decoder.decode(ctx, in, out);

        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof DefaultSocks5CommandRequest);
        DefaultSocks5CommandRequest request = (DefaultSocks5CommandRequest) out.get(0);
        assertEquals(Socks5CommandType.CONNECT, request.type());
        assertEquals(Socks5AddressType.IPv4, request.dstAddrType());
        assertEquals("127.0.0.1", request.dstAddr());
        assertEquals(1080, request.dstPort());
    }

    @Test
    void testDecodeUnsupportedVersion() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(0x04); // unsupported version

        List<Object> out = new java.util.ArrayList<>();
        assertThrows(DecoderException.class, () -> decoder.decode(ctx, in, out));
    }

    @Test
    void testDecodeFailure() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(SocksVersion.SOCKS5.byteValue()); // version
        in.writeByte(Socks5CommandType.CONNECT.byteValue()); // command type
        in.writeByte(0x00); // RSV
        in.writeByte(Socks5AddressType.IPv4.byteValue()); // address type
        // Missing address and port

        List<Object> out = new java.util.ArrayList<>();
        decoder.decode(ctx, in, out);

        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof DefaultSocks5CommandRequest);
        DefaultSocks5CommandRequest request = (DefaultSocks5CommandRequest) out.get(0);
        assertTrue(request.decoderResult().isFailure());
    }
}