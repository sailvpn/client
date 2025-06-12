package com.illiad.proxy;

import com.illiad.proxy.codec.v4.V4ServerEncoder;
import com.illiad.proxy.codec.v5.V5AddressDecoder;
import com.illiad.proxy.codec.v5.V5ClientEncoder;
import com.illiad.proxy.codec.v5.V5ServerEncoder;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.handler.Utils;
import com.illiad.proxy.handler.VersionHandler;
import com.illiad.proxy.handler.v4.V4CommandHandler;
import com.illiad.proxy.security.Ssl;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.springframework.stereotype.Component;

@Component
public class Starter {

    public Starter(Params params, Ssl ssl, Utils utils, HandlerNamer namer, V4ServerEncoder v4ServerEncoder, V4CommandHandler v4CommandHandler, V5ServerEncoder v5ServerEncoder, V5ClientEncoder v5ClientEncoder, V5AddressDecoder v5AddressDecoder) {
        // Configure the bootstrap.
        EventLoopGroup bossGroup = new NioEventLoopGroup(3);
        EventLoopGroup workerGroup = new NioEventLoopGroup(4);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(
                                    new LoggingHandler(LogLevel.INFO),
                                    new VersionHandler(ssl, params, namer, v4ServerEncoder, v5ClientEncoder, v4CommandHandler, v5ServerEncoder, v5AddressDecoder, utils));
                        }
                    });
            b.bind(params.getLocalPort()).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
