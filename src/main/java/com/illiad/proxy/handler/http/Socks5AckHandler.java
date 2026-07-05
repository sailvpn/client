package com.illiad.proxy.handler.http;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.v5.RelayHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.socksx.v5.*;

import java.nio.charset.StandardCharsets;

/**
 * Robust SOCKS5 Acknowledgment Handler.
 * Tailored for permanent FrontHandler + "Forward Everything" state routing.
 * Streamlined to eliminate autoRead adjustments in favor of raw buffer pipelines.
 */
public class Socks5AckHandler extends SimpleChannelInboundHandler<Socks5CommandResponse> {
    private final ParamBus bus;
    private final ChannelHandlerContext frontendCtx;

    /**
     * Clean, decoupled constructor passing only necessary dependencies.
     */
    public Socks5AckHandler(ChannelHandlerContext frontendCtx, ParamBus bus) {
        this.frontendCtx = frontendCtx;
        this.bus = bus;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5CommandResponse response) {
        if (response.status() == Socks5CommandStatus.SUCCESS) {
            final Channel backend = ctx.channel();
            final Channel frontend = frontendCtx.channel();

            // 1. RESTRUCTURE BACKEND PIPELINE
            backend.pipeline().addLast(new RelayHandler(frontend, bus));
            // 2. REMOVE TRANSIENT HANDSHAKE HANDLERS ONLY FROM BACKEND
            // Clears your V5ClientDecoder, PseudoResDecoder, etc. via password-prefix
            cleanupPipeline(backend.pipeline(), bus.namer.getPrefix());
            // 3. UNLOCK FRONTHANDLER BUFFER FLOW
            // Flushes your earlyBytesBuffer queue down the wire.
            // For plain text (GET/POST), this instantly pumps the initial browser data to the server.
            FrontHandler frontHandler = frontendCtx.pipeline().get(FrontHandler.class);
            frontHandler.activateTunnel();

            // 4. CONDITIONAL HOOK: SIGNAL ONLY FOR HTTPS CONNECT METHODS
            if (frontHandler.isConnectMethod()) {
                // HTTPS Track: Send '200 Connection Established' to kick off browser TLS
                String establishedMsg = "HTTP/1.1 200 Connection Established\r\n\r\n";
                ByteBuf ok = frontend.alloc().buffer();
                ok.writeBytes(establishedMsg.getBytes(StandardCharsets.UTF_8));
                frontend.writeAndFlush(ok);
            }
            // Plaintext HTTP (GET/POST/FETCH) Track: Stay silent!
            // The origin server is already processing your flushed request and will stream back data.


        } else {
            handleSocksFailure(ctx);
        }
    }

    private void cleanupPipeline(ChannelPipeline p, String prefix) {
        // Safe removal loop using your namer convention
        p.names().stream()
                .filter(name -> name.startsWith(prefix))
                .forEach(p::remove);
    }

    private void handleSocksFailure(ChannelHandlerContext ctx) {
        String errMsg = "HTTP/1.1 502 Bad Gateway\r\n\r\n";
        ByteBuf err = ctx.alloc().buffer();
        err.writeBytes(errMsg.getBytes(StandardCharsets.UTF_8));

        frontendCtx.writeAndFlush(err).addListener(ChannelFutureListener.CLOSE);
        ctx.close();
    }
}
