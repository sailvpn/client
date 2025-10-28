package com.illiad.proxy.handler.v5;

import com.illiad.proxy.handler.Utils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

public final class RelayHandler extends ChannelInboundHandlerAdapter {

    private final Channel relayChannel;
    private final Utils utils;

    public RelayHandler(Channel relayChannel, Utils utils) {
        this.relayChannel = relayChannel;
        this.utils = utils;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (relayChannel.isActive()) {
            relayChannel.writeAndFlush(msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (relayChannel.isActive()) {
            utils.closeOnFlush(relayChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }
}

