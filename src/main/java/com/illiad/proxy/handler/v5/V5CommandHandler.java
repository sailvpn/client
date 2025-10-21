package com.illiad.proxy.handler.v5;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.codec.v5.V5CmdReqDecoder;
import com.illiad.proxy.handler.v5.associate.AssociateHandler;
import io.netty.channel.*;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.*;

public class V5CommandHandler extends SimpleChannelInboundHandler<Socks5Message> {
    private ParamBus bus;

    public V5CommandHandler(ParamBus bus) {
        this.bus = bus;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Socks5Message socksRequest) {

        if (socksRequest instanceof Socks5InitialRequest) {
            // auth support example
            //ctx.pipeline().addFirst(new V5PwdAuthReqDecoder());
            //ctx.write(new DefaultSocks5AuthMethodResponse(Socks5AuthMethod.PASSWORD));
            ctx.pipeline().addFirst(bus.namer.generateName(), new V5CmdReqDecoder(bus));
            ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
        } else if (socksRequest instanceof Socks5PasswordAuthRequest) {
            ctx.pipeline().addFirst(bus.namer.generateName(), new V5CmdReqDecoder(bus));
            ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
        } else if (socksRequest instanceof Socks5CommandRequest socks5CmdRequest) {
            Socks5CommandType commandType = socks5CmdRequest.type();
            if (commandType == Socks5CommandType.CONNECT) {
                ctx.pipeline().addLast(bus.namer.generateName(), new V5ConnectHandler(bus));
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(socksRequest);
            } else if (commandType == Socks5CommandType.UDP_ASSOCIATE) {
                ctx.pipeline().addLast(new AssociateHandler(bus));
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(socksRequest);
            } else if (commandType == Socks5CommandType.BIND) {
                // BIND is not supported in this implementation
                ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, socks5CmdRequest.dstAddrType()));
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
        bus.utils.closeOnFlush(ctx.channel());
    }
}


