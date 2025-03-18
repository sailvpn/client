package com.illiad.handler.v5;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.concurrent.Promise;

public class V5AckHandler extends SimpleChannelInboundHandler<Socks5CommandResponse> {

    private final Promise<Channel> promise;

    public V5AckHandler(Promise<Channel> promise) {
        this.promise = promise;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5CommandResponse response) {

        Channel frontend = promise.getNow();
        // wite response to frontend
        frontend.writeAndFlush(response).addListener(future -> {
            if (future.isSuccess()) {
                // put frontend input onhold until both frontend and backend are ready
                frontend.config().setAutoRead(false);
                // trigger promise as per message status

                if (response.status() == Socks5CommandStatus.SUCCESS) {
                    promise.setSuccess(ctx.channel());
                } else {
                    promise.setFailure(new Exception(response.status().toString()));
                    ctx.fireExceptionCaught(new Exception(response.status().toString()));
                }
            } else {
                promise.setFailure(future.cause());
                ctx.fireExceptionCaught(future.cause());
            }
        });

        ctx.pipeline().remove(this);
    }
}

