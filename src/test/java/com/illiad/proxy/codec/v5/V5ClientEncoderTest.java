package com.illiad.proxy.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;

class V5ClientEncoderTest {

    private V5ClientEncoder encoder;
    private V5AddressEncoder v5AddressEncoder;
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        v5AddressEncoder = mock(V5AddressEncoder.class);
        encoder = new V5ClientEncoder(v5AddressEncoder);
        ctx = mock(ChannelHandlerContext.class);
    }

    @Test
    void testEncodeWithIPv4Address() throws Exception {
        ByteBuf out = Unpooled.buffer();
        Socks5CommandRequest request = new DefaultSocks5CommandRequest(
                Socks5CommandType.CONNECT,
                Socks5AddressType.IPv4,
                "127.0.0.1",
                1080
        );

        doAnswer(invocation -> {
            ByteBuf buffer = invocation.getArgument(2);
            buffer.writeBytes(new byte[]{127, 0, 0, 1}); // Mock encoding of IPv4 address
            return null;
        }).when(v5AddressEncoder).encodeAddress(eq(Socks5AddressType.IPv4), eq("127.0.0.1"), any(ByteBuf.class));

        encoder.encode(ctx, request, out);

        byte[] expected = new byte[]{
                0x05, // version
                Socks5CommandType.CONNECT.byteValue(), // command type
                0x00, // reserved
                Socks5AddressType.IPv4.byteValue(), // address type
                127, 0, 0, 1, // address (127.0.0.1)
                0x04, 0x38 // port (1080 in big-endian)
        };

        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);

        assertArrayEquals(expected, actual);
        verify(ctx.pipeline()).remove(encoder);
    }

    @Test
    void testEncodeWithDomainName() throws Exception {
        ByteBuf out = Unpooled.buffer();
        Socks5CommandRequest request = new DefaultSocks5CommandRequest(
                Socks5CommandType.CONNECT,
                Socks5AddressType.DOMAIN,
                "example.com",
                1080
        );

        doAnswer(invocation -> {
            ByteBuf buffer = invocation.getArgument(2);
            buffer.writeByte(11); // Mock length of domain name
            buffer.writeBytes("example.com".getBytes()); // Mock encoding of domain name
            return null;
        }).when(v5AddressEncoder).encodeAddress(eq(Socks5AddressType.DOMAIN), eq("example.com"), any(ByteBuf.class));

        encoder.encode(ctx, request, out);

        byte[] expected = new byte[]{
                0x05, // version
                Socks5CommandType.CONNECT.byteValue(), // command type
                0x00, // reserved
                Socks5AddressType.DOMAIN.byteValue(), // address type
                0x0B, // domain length
                'e', 'x', 'a', 'm', 'p', 'l', 'e', '.', 'c', 'o', 'm', // domain name
                0x04, 0x38 // port (1080 in big-endian)
        };

        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);

        assertArrayEquals(expected, actual);
        verify(ctx.pipeline()).remove(encoder);
    }
}