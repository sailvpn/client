package com.illiad.proxy.codec.v4;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V4ServerDecoderTest {

    private V4ServerDecoder decoder;
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        decoder = new V4ServerDecoder();
        ctx = mock(ChannelHandlerContext.class);
    }

    @Test
    void testDecodeValidRequest() throws Exception {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(SocksVersion.SOCKS4a.byteValue()); // version
        in.writeByte(Socks4CommandType.CONNECT.byteValue()); // command type
        in.writeShort(1080); // dstPort
        in.writeInt(0x7F000001); // dstAddr (127.0.0.1)
        in.writeBytes("user".getBytes()); // userId
        in.writeByte(0x00); // NUL terminator

        List<Object> out = new java.util.ArrayList<>();
        decoder.decode(ctx, in, out);

        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof DefaultSocks4CommandRequest);
        DefaultSocks4CommandRequest request = (DefaultSocks4CommandRequest) out.get(0);
        assertEquals(Socks4CommandType.CONNECT, request.type());
        assertEquals("127.0.0.1", request.dstAddr());
        assertEquals(1080, request.dstPort());
        assertEquals("user", request.userId());
    }

    @Test
    void testDecodeUnsupportedVersion() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(0x05); // unsupported version

        List<Object> out = new java.util.ArrayList<>();
        assertThrows(DecoderException.class, () -> decoder.decode(ctx, in, out));
    }

    @Test
    void testDecodeFailure() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(SocksVersion.SOCKS4a.byteValue()); // version
        in.writeByte(Socks4CommandType.CONNECT.byteValue()); // command type
        in.writeShort(1080); // dstPort
        in.writeInt(0x7F000001); // dstAddr (127.0.0.1)
        // Missing userId and NUL terminator

        List<Object> out = new java.util.ArrayList<>();
        decoder.decode(ctx, in, out);

        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof DefaultSocks4CommandRequest);
        DefaultSocks4CommandRequest request = (DefaultSocks4CommandRequest) out.get(0);
        assertTrue(request.decoderResult().isFailure());
    }
}