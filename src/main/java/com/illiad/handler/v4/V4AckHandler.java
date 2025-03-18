package com.illiad.handler.v4;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v4.Socks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.util.concurrent.Promise;

public class V4AckHandler extends SimpleChannelInboundHandler<Socks4CommandResponse> {

    private final Promise<Channel> promise;

    public V4AckHandler(Promise<Channel> promise) {
        this.promise = promise;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks4CommandResponse response) {
        Channel frontend = promise.getNow();
        // write response to frontend
        frontend.writeAndFlush(response).addListener(future -> {
            if (future.isSuccess()) {
                // put frontend input onhold until both frontend and backend are ready
                frontend.config().setAutoRead(false);
                // trigger promise as per message status
                if (response.status() == Socks4CommandStatus.SUCCESS) {
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