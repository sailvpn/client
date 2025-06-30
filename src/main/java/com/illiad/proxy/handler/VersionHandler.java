package com.illiad.proxy.handler;

import com.illiad.proxy.HandlerNamer;
import com.illiad.proxy.codec.v5.V5AddressDecoder;
import com.illiad.proxy.codec.v5.V5ClientEncoder;
import com.illiad.proxy.codec.v5.V5InitReqDecoder;
import com.illiad.proxy.codec.v5.V5ServerEncoder;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.handler.v5.V5CommandHandler;
import com.illiad.proxy.security.Ssl;
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

    private final V5AddressDecoder v5AddressDecoder;
    private final Ssl ssl;
    private final Params params;
    private final HandlerNamer namer;
    private final V5ClientEncoder v5ClientEncoder;
    private final V5ServerEncoder v5ServerEncoder;
    private final Utils utils;

    public VersionHandler(Ssl ssl, Params params, HandlerNamer namer, V5ClientEncoder v5ClientEncoder, V5ServerEncoder v5ServerEncoder, V5AddressDecoder v5AddressDecoder, Utils utils) {
        this.ssl = ssl;
        this.params = params;
        this.namer = namer;
        this.v5ClientEncoder = v5ClientEncoder;
        this.v5ServerEncoder = v5ServerEncoder;
        this.v5AddressDecoder = v5AddressDecoder;
        this.utils = utils;
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
                p.addLast(namer.generateName(), v5ServerEncoder);
                p.addLast(namer.generateName(), new V5InitReqDecoder());
                p.addLast(namer.generateName(), new V5CommandHandler(ssl, params, namer, v5ClientEncoder, v5AddressDecoder, utils));
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
