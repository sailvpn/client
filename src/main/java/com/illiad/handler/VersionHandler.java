package com.illiad.handler;

import com.illiad.codec.v4.V4ServerDecoder;
import com.illiad.codec.v4.V4ServerEncoder;
import com.illiad.codec.v5.V5InitReqDecoder;
import com.illiad.codec.v5.V5ServerEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the version of the current SOCKS connection and initializes the pipeline with
 * either {@link V4ServerDecoder} or {@link V5InitReqDecoder}.
 */

@Component
@ChannelHandler.Sharable
public class VersionHandler extends ByteToMessageDecoder {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(VersionHandler.class);

    private final V4ServerEncoder v4ServerEncoder;
    private final V5ServerEncoder v5ServerEncoder;

    public VersionHandler(V4ServerEncoder v4ServerEncoder, V5ServerEncoder v5ServerEncoder) {
        this.v4ServerEncoder = v4ServerEncoder;
        this.v5ServerEncoder = v5ServerEncoder;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        final int readerIndex = in.readerIndex();
        if (in.writerIndex() == readerIndex) {
            return;
        }

        ChannelPipeline p = ctx.pipeline();
        final byte versionVal = in.getByte(readerIndex);
        SocksVersion version = SocksVersion.valueOf(versionVal);

        switch (version) {
            case SOCKS4a:
                logKnownVersion(ctx, version);
                p.addAfter(ctx.name(), null, v4ServerEncoder);
                p.addAfter(ctx.name(), null, new V4ServerDecoder());
                break;
            case SOCKS5:
                logKnownVersion(ctx, version);
                p.addAfter(ctx.name(), null, v5ServerEncoder);
                p.addAfter(ctx.name(), null, new V5InitReqDecoder());
                break;
            default:
                logUnknownVersion(ctx, versionVal);
                in.skipBytes(in.readableBytes());
                ctx.close();
                return;
        }

        p.remove(this);
    }

    private static void logKnownVersion(ChannelHandlerContext ctx, SocksVersion version) {
        logger.debug("{} Protocol version: {}({})", ctx.channel(), version);
    }

    private static void logUnknownVersion(ChannelHandlerContext ctx, byte versionVal) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} Unknown protocol version: {}", ctx.channel(), versionVal & 0xFF);
        }
    }
}
