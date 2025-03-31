package com.illiad.proxy.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class V5ClientDecoderTest {
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private V5AddressDecoder v5AddressDecoder;
    @InjectMocks
    private V5ClientDecoder decoder;

    @Test
    void testDecodeValidResponse() throws Exception {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(SocksVersion.SOCKS5.byteValue()); // version
        in.writeByte(Socks5CommandStatus.SUCCESS.byteValue()); // status
        in.writeByte(0x00); // Reserved
        in.writeByte(Socks5AddressType.IPv4.byteValue()); // address type
        in.writeBytes(new byte[]{127, 0, 0, 1}); // address (127.0.0.1)
        in.writeShort(1080); // port

        when(v5AddressDecoder.decodeAddress(Socks5AddressType.IPv4, in)).thenReturn("127.0.0.1");

        List<Object> out = new java.util.ArrayList<>();
        decoder.decode(ctx, in, out);

        // out.size = 2, the 2nd one is the null exception trigger by "ctx.pipeline().remove(this).." line 53
        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof DefaultSocks5CommandResponse);
        DefaultSocks5CommandResponse response = (DefaultSocks5CommandResponse) out.get(0);
        assertEquals(Socks5CommandStatus.SUCCESS, response.status());
        assertEquals(Socks5AddressType.IPv4, response.bndAddrType());
        assertEquals("127.0.0.1", response.bndAddr());
        assertEquals(1080, response.bndPort());
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
        in.writeByte(Socks5CommandStatus.FAILURE.byteValue()); // status
        in.writeByte(0x00); // Reserved
        in.writeByte(Socks5AddressType.IPv4.byteValue()); // address type
        // Missing address and port

        List<Object> out = new java.util.ArrayList<>();
        decoder.decode(ctx, in, out);

        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof DefaultSocks5CommandResponse);
        DefaultSocks5CommandResponse response = (DefaultSocks5CommandResponse) out.get(0);
        assertTrue(response.decoderResult().isFailure());
    }
}