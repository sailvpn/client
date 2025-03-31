package com.illiad.proxy.handler;

import com.illiad.proxy.codec.v4.V4ServerDecoder;
import com.illiad.proxy.codec.v4.V4ServerEncoder;
import com.illiad.proxy.codec.v5.V5InitReqDecoder;
import com.illiad.proxy.codec.v5.V5ServerEncoder;
import com.illiad.proxy.handler.v4.V4CommandHandler;
import com.illiad.proxy.handler.v5.V5CommandHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.SocksVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.mockito.Mockito.*;

class VersionHandlerTest {

    private VersionHandler versionHandler;
    private V4ServerEncoder v4ServerEncoder;
    private V5ServerEncoder v5ServerEncoder;
    private V4CommandHandler v4CommandHandler;
    private V5CommandHandler v5CommandHandler;
    private ChannelHandlerContext ctx;
    private ChannelPipeline pipeline;

    @BeforeEach
    void setUp() {
        v4ServerEncoder = mock(V4ServerEncoder.class);
        v5ServerEncoder = mock(V5ServerEncoder.class);
        v4CommandHandler = mock(V4CommandHandler.class);
        v5CommandHandler = mock(V5CommandHandler.class);

        versionHandler = new VersionHandler(v4ServerEncoder, v4CommandHandler, v5ServerEncoder, v5CommandHandler);

        ctx = mock(ChannelHandlerContext.class);
        pipeline = mock(ChannelPipeline.class);

        when(ctx.pipeline()).thenReturn(pipeline);
        when(ctx.name()).thenReturn("versionHandler");
    }

    @Test
    void testDecodeSocks4a() throws Exception {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(SocksVersion.SOCKS4a.byteValue()); // SOCKS4a version

        versionHandler.decode(ctx, in, mock(List.class));

        verify(pipeline).addAfter(eq("versionHandler"), any(), eq(v4ServerEncoder));
        verify(pipeline).addAfter(eq("versionHandler"), any(), any(V4ServerDecoder.class));
        verify(pipeline).addAfter(eq("versionHandler"), any(), eq(v4CommandHandler));
        verify(pipeline).remove(versionHandler);
    }

    @Test
    void testDecodeSocks5() throws Exception {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(SocksVersion.SOCKS5.byteValue()); // SOCKS5 version

        versionHandler.decode(ctx, in, mock(List.class));

        verify(pipeline).addAfter(eq("versionHandler"), any(), eq(v5ServerEncoder));
        verify(pipeline).addAfter(eq("versionHandler"), any(), any(V5InitReqDecoder.class));
        verify(pipeline).addAfter(eq("versionHandler"), any(), eq(v5CommandHandler));
        verify(pipeline).remove(versionHandler);
    }

    @Test
    void testDecodeUnknownVersion() throws Exception {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(0x03); // Unknown version

        versionHandler.decode(ctx, in, mock(List.class));

        verify(ctx).close();
        verify(pipeline, never()).addAfter(any(), any(), any());
    }
}