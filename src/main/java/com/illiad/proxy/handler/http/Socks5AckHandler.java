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

            FrontHandler frontHandler = (FrontHandler) frontendCtx.pipeline().get("frontHandler");

            // Guard against race condition where client closed connection during SOCKS handshake
            if (!frontend.isActive() || frontHandler == null) {
                backend.close();
                return;
            }

            // 1. Restructure backend pipeline
            backend.pipeline().addLast(new RelayHandler(frontend, bus));
            cleanupPipeline(backend.pipeline(), bus.namer.getPrefix());

            // 2. For HTTPS CONNECT, send 200 OK *before* unlocking tunnel so client gets 200 first
            if (frontHandler.isConnectMethod()) {
                String establishedMsg = "HTTP/1.1 200 Connection Established\r\n\r\n";
                ByteBuf ok = frontend.alloc().buffer(establishedMsg.length());
                ok.writeBytes(establishedMsg.getBytes(StandardCharsets.UTF_8));
                frontend.writeAndFlush(ok);
            }

            // 3. Unlock FrontHandler and flush cached early bytes
            frontHandler.activateTunnel();

        } else {
            handleSocksFailure(ctx);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
        if (frontendCtx.channel().isActive()) {
            frontendCtx.close();
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
