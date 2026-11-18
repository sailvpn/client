package com.illiad.proxy.handler.v5;

import com.illiad.proxy.ParamBus;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

public final class RelayHandler extends ChannelInboundHandlerAdapter {

    private final Channel relayChannel;
    private final ParamBus bus;

    public RelayHandler(Channel relayChannel, ParamBus bus) {
        this.relayChannel = relayChannel;
        this.bus = bus;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (relayChannel != null && relayChannel.isActive() && msg instanceof ByteBuf) {
            relayChannel.writeAndFlush(msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (relayChannel != null && relayChannel.isActive()) {
            bus.utils.closeOnFlush(relayChannel);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Do NOT call ctx.fireExceptionCaught(cause) here since this is the tail handler
        if (relayChannel != null && relayChannel.isActive()) {
            bus.utils.closeOnFlush(relayChannel);
        }
        ctx.close();
    }
}


