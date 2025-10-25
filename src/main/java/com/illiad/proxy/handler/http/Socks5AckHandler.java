package com.illiad.proxy.handler.http;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.v5.RelayHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.ReferenceCountUtil;

public class Socks5AckHandler extends SimpleChannelInboundHandler<Socks5CommandResponse> {

    private final ChannelHandlerContext frontendCtx;
    private final ParamBus bus;
    private final HttpRequest initialReq;

    public Socks5AckHandler(ChannelHandlerContext frontendCtx, ParamBus bus, HttpRequest initialReq) {
        this.frontendCtx = frontendCtx;
        this.bus = bus;
        this.initialReq = initialReq;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5CommandResponse response) {
        try {
            if (response.status() == Socks5CommandStatus.SUCCESS) {

                Channel frontend = frontendCtx.channel();
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
                // remove all handlers from the frontendPipeLine
                for (String name : frontendPipeline.names()) {
                    if (name.startsWith(prefix)) {
                        frontendPipeline.remove(name);
                    }
                }

                // forward the first httpRequest
                backend.writeAndFlush(initialReq).sync();
                ReferenceCountUtil.release(initialReq);
            } else {
                frontendCtx.fireExceptionCaught(new Exception(response.status().toString()));
                bus.utils.closeOnFlush(frontendCtx.channel());
                bus.utils.closeOnFlush(ctx.channel());
            }

        } catch (InterruptedException e) {
            frontendCtx.fireExceptionCaught(new RuntimeException(e));
        }
    }
}

