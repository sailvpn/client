package com.illiad.proxy.codec;

import com.illiad.proxy.security.Secret;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.SocksMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;

class HeaderEncoderTest {

    private Secret secret;
    private Header header;
    private HeaderEncoder headerEncoder;

    @BeforeEach
    void setUp() {
        secret = mock(Secret.class);
        header = mock(Header.class);
        headerEncoder = new HeaderEncoder(secret, header);
    }

    @Test
    void testEncode() throws Exception {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        SocksMessage socksMessage = mock(SocksMessage.class);
        ByteBuf byteBuf = Unpooled.buffer();

        byte[] offset = new byte[]{0x01, 0x02, 0x03};
        byte[] secretBytes = new byte[]{0x04, 0x05, 0x06, 0x07};

        when(header.offset()).thenReturn(offset);
        when(secret.getSecret()).thenReturn(secretBytes);

        headerEncoder.encode(ctx, socksMessage, byteBuf);

        byte[] expected = new byte[]{
                0x03, 0x01, 0x02, 0x03, 0x0D, 0x0A, // offset and CRLF
                0x04, 0x00, 0x04, 0x05, 0x06, 0x07, 0x0D, 0x0A // secret and CRLF
        };

        byte[] actual = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(actual);

        assertArrayEquals(expected, actual);
    }
}