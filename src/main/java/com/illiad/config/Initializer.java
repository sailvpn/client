package com.illiad.config;

import com.illiad.handler.CommandHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.springframework.stereotype.Component;

@Component
public class Initializer extends ChannelInitializer<SocketChannel> {

    private final CommandHandler commandHandler;

    public Initializer(CommandHandler commandHandler){
        this.commandHandler = commandHandler;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(
                new LoggingHandler(LogLevel.INFO),
                new SocksPortUnificationServerHandler(),
                commandHandler);
    }
}