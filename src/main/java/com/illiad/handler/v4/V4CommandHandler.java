package com.illiad.handler.v4;

import com.illiad.handler.ConnectHandler;
import com.illiad.handler.Utils;
import io.netty.channel.*;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class V4CommandHandler extends SimpleChannelInboundHandler<Socks4CommandRequest> {
    private final ConnectHandler connectHandler;
    private final Utils utils;

    public V4CommandHandler(ConnectHandler connectHandler, Utils utils) {
        this.connectHandler = connectHandler;
        this.utils = utils;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Socks4CommandRequest request) {

        if (request.type() == Socks4CommandType.CONNECT) {
            ctx.pipeline().addLast(connectHandler);
            ctx.pipeline().remove(this);
            ctx.fireChannelRead(request);
        } else {
            ctx.close();
        }

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        ctx.fireExceptionCaught(throwable);
        utils.closeOnFlush(ctx.channel());
    }
}