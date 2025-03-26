package com.illiad.proxy.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v5.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import io.netty.handler.codec.EncoderException;

class V5ServerEncoderTest {

    private V5ServerEncoder encoder;
    private V5AddressEncoder v5AddressEncoder;
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        v5AddressEncoder = mock(V5AddressEncoder.class);
        encoder = new V5ServerEncoder(v5AddressEncoder);
        ctx = mock(ChannelHandlerContext.class);
    }

    @Test
    void testEncodeAuthMethodResponse() throws Exception {
        ByteBuf out = Unpooled.buffer();
        Socks5InitialResponse response = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH);

        encoder.encode(ctx, response, out);

        byte[] expected = new byte[]{
                0x05, // version
                Socks5AuthMethod.NO_AUTH.byteValue() // auth method
        };

        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);

        assertArrayEquals(expected, actual);
    }

    @Test
    void testEncodePasswordAuthResponse() throws Exception {
        ByteBuf out = Unpooled.buffer();
        Socks5PasswordAuthResponse response = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS);

        encoder.encode(ctx, response, out);

        byte[] expected = new byte[]{
                0x01, // version
                Socks5PasswordAuthStatus.SUCCESS.byteValue() // status
        };

        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);

        assertArrayEquals(expected, actual);
    }

    @Test
    void testEncodeCommandResponse() throws Exception {
        ByteBuf out = Unpooled.buffer();
        Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS,
                Socks5AddressType.IPv4,
                "127.0.0.1",
                1080
        );

        doAnswer(invocation -> {
            ByteBuf buffer = invocation.getArgument(2);
            buffer.writeBytes(new byte[]{127, 0, 0, 1}); // Mock encoding of IPv4 address
            return null;
        }).when(v5AddressEncoder).encodeAddress(eq(Socks5AddressType.IPv4), eq("127.0.0.1"), any(ByteBuf.class));

        encoder.encode(ctx, response, out);

        byte[] expected = new byte[]{
                0x05, // version
                Socks5CommandStatus.SUCCESS.byteValue(), // status
                0x00, // reserved
                Socks5AddressType.IPv4.byteValue(), // address type
                127, 0, 0, 1, // address (127.0.0.1)
                0x04, 0x38 // port (1080 in big-endian)
        };

        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);

        assertArrayEquals(expected, actual);
    }

    @Test
    void testUnsupportedMessageType() {
        ByteBuf out = Unpooled.buffer();
        Socks5Message unsupportedMessage = mock(Socks5Message.class);

        assertThrows(EncoderException.class, () -> encoder.encode(ctx, unsupportedMessage, out));
    }
}