package com.illiad.proxy.codec.v4;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;

class V4ServerEncoderTest {

    private V4ServerEncoder encoder;
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        encoder = new V4ServerEncoder();
        ctx = mock(ChannelHandlerContext.class);
    }

    @Test
    void testEncodeWithValidAddress() throws Exception {
        ByteBuf out = Unpooled.buffer();
        Socks4CommandResponse response = new DefaultSocks4CommandResponse(
                Socks4CommandStatus.SUCCESS,
                "127.0.0.1",
                1080
        );

        encoder.encode(ctx, response, out);

        byte[] expected = new byte[]{
                0x00, // version
                Socks4CommandStatus.SUCCESS.byteValue(), // status
                0x04, 0x38, // dstPort (1080 in big-endian)
                127, 0, 0, 1 // dstAddr (127.0.0.1)
        };

        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);

        assertArrayEquals(expected, actual);
    }

    @Test
    void testEncodeWithNullAddress() throws Exception {
        ByteBuf out = Unpooled.buffer();
        Socks4CommandResponse response = new DefaultSocks4CommandResponse(
                Socks4CommandStatus.REJECTED_OR_FAILED,
                null,
                0
        );

        encoder.encode(ctx, response, out);

        byte[] expected = new byte[]{
                0x00, // version
                Socks4CommandStatus.REJECTED_OR_FAILED.byteValue(), // status
                0x00, 0x00, // dstPort (0 in big-endian)
                0x00, 0x00, 0x00, 0x00 // IPv4_HOSTNAME_ZEROED
        };

        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);

        assertArrayEquals(expected, actual);
    }
}