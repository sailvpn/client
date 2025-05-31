package com.illiad.proxy.handler.v5;

import com.illiad.proxy.HandlerNamer;
import com.illiad.proxy.handler.RelayHandler;
import com.illiad.proxy.handler.Utils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;

import java.util.concurrent.ExecutionException;

public class V5AckHandler extends SimpleChannelInboundHandler<Socks5CommandResponse> {

    private final ChannelHandlerContext frontendCtx;
    private final HandlerNamer namer;
    private final Utils utils;

    public V5AckHandler(ChannelHandlerContext frontendCtx, HandlerNamer namer, Utils utils) {
        this.frontendCtx = frontendCtx;
        this.namer = namer;
        this.utils = utils;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5CommandResponse response) throws ExecutionException, InterruptedException {

        if (response.status() == Socks5CommandStatus.SUCCESS) {
            // wite response to frontend
            Channel frontend = frontendCtx.channel();
            frontend.writeAndFlush(response).addListener(future -> {
                if (future.isSuccess()) {
                    final ChannelPipeline frontendPipeline = frontend.pipeline();
                    final Channel backend = ctx.channel();
                    final ChannelPipeline backendPipeline = backend.pipeline();
                    // setup Socks direct channel relay between frontend and backend
                    frontendPipeline.addLast(new RelayHandler(backend, utils));
                    backendPipeline.addLast(new RelayHandler(frontend, utils));
                    String prefix = namer.getPrefix();
                    // remove all handlers except SslHandler from backendPipeline
                    for (String name : backendPipeline.names()) {
                        if (name.startsWith(prefix)) {
                            backendPipeline.remove(name);
                        }
                    }
                    // remove all handlers except LoggingHandler from frontendPipeline
                    for (String name : frontendPipeline.names()) {
                        if (name.startsWith(prefix)) {
                            frontendPipeline.remove(name);
                        }
                    }
                } else {
                    frontendCtx.fireExceptionCaught(future.cause());
                    utils.closeOnFlush(frontend);
                    utils.closeOnFlush(ctx.channel());
                }
            });
        } else {
            frontendCtx.fireExceptionCaught(new Exception(response.status().toString()));
            utils.closeOnFlush(frontendCtx.channel());
            utils.closeOnFlush(ctx.channel());
        }

    }
}

