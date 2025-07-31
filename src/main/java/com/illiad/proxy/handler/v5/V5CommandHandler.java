package com.illiad.proxy.handler.v5;

import com.illiad.proxy.HandlerNamer;
import com.illiad.proxy.codec.v5.V5AddressDecoder;
import com.illiad.proxy.codec.v5.V5ClientEncoder;
import com.illiad.proxy.codec.v5.V5CmdReqDecoder;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.handler.Utils;
import com.illiad.proxy.security.Ssl;
import io.netty.channel.*;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.*;

public class V5CommandHandler extends SimpleChannelInboundHandler<Socks5Message> {
    private final Ssl ssl;
    private final Params params;
    private final HandlerNamer namer;
    private final V5ClientEncoder v5ClientEncoder;
    private final V5AddressDecoder v5AddressDecoder;
    private final Utils utils;

    public V5CommandHandler(Ssl ssl, Params params, HandlerNamer namer, V5ClientEncoder v5ClientEncoder, V5AddressDecoder v5AddressDecoder, Utils utils) {
        this.ssl = ssl;
        this.params = params;
        this.namer = namer;
        this.v5ClientEncoder = v5ClientEncoder;
        this.v5AddressDecoder = v5AddressDecoder;
        this.utils = utils;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Socks5Message socksRequest) {

        if (socksRequest instanceof Socks5InitialRequest) {
            // auth support example
            //ctx.pipeline().addFirst(new V5PwdAuthReqDecoder());
            //ctx.write(new DefaultSocks5AuthMethodResponse(Socks5AuthMethod.PASSWORD));
            ctx.pipeline().addFirst(namer.generateName(), new V5CmdReqDecoder(this.v5AddressDecoder));
            ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
        } else if (socksRequest instanceof Socks5PasswordAuthRequest) {
            ctx.pipeline().addFirst(namer.generateName(), new V5CmdReqDecoder(this.v5AddressDecoder));
            ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
        } else if (socksRequest instanceof Socks5CommandRequest socks5CmdRequest) {
            Socks5CommandType commandType = socks5CmdRequest.type();
            if (commandType == Socks5CommandType.CONNECT || commandType == Socks5CommandType.UDP_ASSOCIATE) {
                ctx.pipeline().addLast(namer.generateName(), new V5ConnectHandler(ssl, params, namer, v5ClientEncoder, v5AddressDecoder, utils));
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


