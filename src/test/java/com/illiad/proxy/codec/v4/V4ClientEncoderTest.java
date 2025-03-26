package com.illiad.proxy.codec.v4;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;

class V4ClientEncoderTest {

    private V4ClientEncoder encoder;
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        encoder = new V4ClientEncoder();
        ctx = mock(ChannelHandlerContext.class);
    }

    @Test
    void testEncodeWithIPv4Address() throws Exception {
        ByteBuf out = Unpooled.buffer();
        Socks4CommandRequest request = new DefaultSocks4CommandRequest(
                Socks4CommandType.CONNECT,
                "127.0.0.1",
                1080,
                "user"
        );

        encoder.encode(ctx, request, out);

        byte[] expected = new byte[]{
                0x04, // version
                Socks4CommandType.CONNECT.byteValue(), // command type
                0x04, 0x38, // dstPort (1080 in big-endian)
                127, 0, 0, 1, // dstAddr (127.0.0.1)
                'u', 's', 'e', 'r', 0x00 // userId + null terminator
        };

        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);

        assertArrayEquals(expected, actual);
    }

    @Test
    void testEncodeWithDomainName() throws Exception {
        ByteBuf out = Unpooled.buffer();
        Socks4CommandRequest request = new DefaultSocks4CommandRequest(
                Socks4CommandType.CONNECT,
                "example.com",
                1080,
                "user"
        );

        encoder.encode(ctx, request, out);

        byte[] expected = new byte[]{
                0x04, // version
                Socks4CommandType.CONNECT.byteValue(), // command type
                0x04, 0x38, // dstPort (1080 in big-endian)
                0x00, 0x00, 0x00, 0x01, // IPv4 domain marker
                'u', 's', 'e', 'r', 0x00, // userId + null terminator
                'e', 'x', 'a', 'm', 'p', 'l', 'e', '.', 'c', 'o', 'm', 0x00 // domain name + null terminator
        };

        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);

        assertArrayEquals(expected, actual);
    }
}