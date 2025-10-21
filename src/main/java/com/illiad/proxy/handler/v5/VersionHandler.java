package com.illiad.proxy.handler.v5;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.codec.v5.V5InitReqDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.List;

/**
 * Detects the version of the current SOCKS connection and initializes the pipeline with {@link V5InitReqDecoder}.
 */

public class VersionHandler extends ByteToMessageDecoder {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(VersionHandler.class);
    private final ParamBus bus;

    public VersionHandler(ParamBus bus) {
        this.bus = bus;
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
            case SOCKS5:
                logKnownVersion(ctx, version);
                p.addLast(bus.namer.generateName(), bus.v5ServerEncoder);
                p.addLast(bus.namer.generateName(), new V5InitReqDecoder());
                p.addLast(bus.namer.generateName(), new V5CommandHandler(bus));
                break;
            case SOCKS4a:
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
