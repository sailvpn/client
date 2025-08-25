package com.illiad.proxy.handler.v5;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.RelayHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;

import java.util.concurrent.ExecutionException;

public class V5AckHandler extends SimpleChannelInboundHandler<Socks5CommandResponse> {

    private final ChannelHandlerContext frontendCtx;
    private final ParamBus bus;

    public V5AckHandler(ChannelHandlerContext frontendCtx, ParamBus bus) {
        this.frontendCtx = frontendCtx;
        this.bus = bus;
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
                    frontendPipeline.addLast(new RelayHandler(backend, bus.utils));
                    backendPipeline.addLast(new RelayHandler(frontend, bus.utils));
                    String prefix = bus.namer.getPrefix();
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
                    bus.utils.closeOnFlush(frontend);
                    bus.utils.closeOnFlush(ctx.channel());
                }
            });
        } else {
            frontendCtx.fireExceptionCaught(new Exception(response.status().toString()));
            bus.utils.closeOnFlush(frontendCtx.channel());
            bus.utils.closeOnFlush(ctx.channel());
        }
    }
}

