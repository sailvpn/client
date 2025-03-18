package com.illiad.handler.v5;

import com.illiad.codec.v5.V5CmdReqDecoder;
import com.illiad.handler.Utils;
import io.netty.channel.*;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.*;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class V5CommandHandler extends SimpleChannelInboundHandler<Socks5Message> {
    private final V5ConnectHandler connectHandler;
    private final Utils utils;

    public V5CommandHandler(V5ConnectHandler connectHandler, Utils utils) {
        this.connectHandler = connectHandler;
        this.utils = utils;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Socks5Message socksRequest) {

        if (socksRequest instanceof Socks5InitialRequest) {
            // auth support example
            //ctx.pipeline().addFirst(new V5PwdAuthReqDecoder());
            //ctx.write(new DefaultSocks5AuthMethodResponse(Socks5AuthMethod.PASSWORD));
            ctx.pipeline().addFirst(new V5CmdReqDecoder());
            ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
            //} else if (socksRequest instanceof Socks5PasswordAuthRequest) {
            //    ctx.pipeline().addFirst(new V5CmdReqDecoder());
            //    ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
        } else if (socksRequest instanceof Socks5CommandRequest socks5CmdRequest) {
            if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
                ctx.pipeline().addLast(connectHandler);
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(socksRequest);
            } else {
                ctx.close();
            }
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


