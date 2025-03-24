package com.illiad.proxy.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.SocksMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;

class HeaderEncoderTest {

    private Header header;
    private HeaderEncoder headerEncoder;
    private ChannelHandlerContext ctx;
    private SocksMessage socksMessage;

    @BeforeEach
    void setUp() {
        header = mock(Header.class);
        headerEncoder = new HeaderEncoder(header);
        ctx = mock(ChannelHandlerContext.class);
        socksMessage = mock(SocksMessage.class);
    }

    @Test
    void testEncode() throws Exception {
        byte[] offset = "offset".getBytes();
        byte[] secret = "secret".getBytes();

        when(header.offset()).thenReturn(offset);
        when(header.getSecret().getSecret()).thenReturn(secret);

        ByteBuf byteBuf = Unpooled.buffer();
        headerEncoder.encode(ctx, socksMessage, byteBuf);

        byte[] expectedOutput = new byte[offset.length + secret.length + 6];
        expectedOutput[0] = (byte) offset.length;
        System.arraycopy(offset, 0, expectedOutput, 1, offset.length);
        expectedOutput[offset.length + 1] = 0x0D;
        expectedOutput[offset.length + 2] = 0x0A;
        expectedOutput[offset.length + 3] = (byte) secret.length;
        expectedOutput[offset.length + 4] = 0;
        System.arraycopy(secret, 0, expectedOutput, offset.length + 5, secret.length);
        expectedOutput[offset.length + secret.length + 5] = 0x0D;
        expectedOutput[offset.length + secret.length + 6] = 0x0A;

        byte[] actualOutput = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(actualOutput);

        assertArrayEquals(expectedOutput, actualOutput);
    }
}