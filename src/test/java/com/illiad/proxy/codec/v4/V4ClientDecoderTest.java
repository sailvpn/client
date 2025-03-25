package com.illiad.proxy.codec.v4;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V4ClientDecoderTest {

    private V4ClientDecoder decoder;
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        decoder = new V4ClientDecoder();
        ctx = mock(ChannelHandlerContext.class);
    }

    @Test
    void testDecodeValidResponse() throws Exception {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(0x00); // version
        in.writeByte(Socks4CommandStatus.SUCCESS.byteValue()); // status
        in.writeShort(1080); // dstPort
        in.writeInt(0x7F000001); // dstAddr (127.0.0.1)

        List<Object> out = new java.util.ArrayList<>();
        decoder.decode(ctx, in, out);

        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof DefaultSocks4CommandResponse);
        DefaultSocks4CommandResponse response = (DefaultSocks4CommandResponse) out.get(0);
        assertEquals(Socks4CommandStatus.SUCCESS, response.status());
        assertEquals("127.0.0.1", response.dstAddr());
        assertEquals(1080, response.dstPort());
    }

    @Test
    void testDecodeUnsupportedVersion() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(0x01); // unsupported version

        List<Object> out = new java.util.ArrayList<>();
        assertThrows(DecoderException.class, () -> decoder.decode(ctx, in, out));
    }

    @Test
    void testDecodeFailure() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(0x00); // version
        in.writeByte(Socks4CommandStatus.REJECTED_OR_FAILED.byteValue()); // status
        // Missing dstPort and dstAddr

        List<Object> out = new java.util.ArrayList<>();
        decoder.decode(ctx, in, out);

        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof DefaultSocks4CommandResponse);
        DefaultSocks4CommandResponse response = (DefaultSocks4CommandResponse) out.get(0);
        assertTrue(response.decoderResult().isFailure());
    }
}