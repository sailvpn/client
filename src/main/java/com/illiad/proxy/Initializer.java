package com.illiad.proxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class Initializer extends ChannelInitializer<SocketChannel> {

    Params params;

    public Initializer(Params params) {
        this.params = params;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(
                new LoggingHandler(LogLevel.INFO),
                new SocksPortUnificationServerHandler(),
                new FrontEndHandler(params));
    }
}