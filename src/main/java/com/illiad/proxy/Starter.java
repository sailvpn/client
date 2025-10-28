package com.illiad.proxy;

import com.illiad.proxy.handler.http.FrontHandler;
import com.illiad.proxy.handler.v5.VersionHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class Starter {

    private final ParamBus bus;
    private final List<Channel> channels = new ArrayList<>();

    // Create separate, independent EventLoopGroups for each server
    private final EventLoopGroup socksBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup socksWorkerGroup = new NioEventLoopGroup(2);

    private final EventLoopGroup httpBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup httpWorkerGroup = new NioEventLoopGroup(2);

    public Starter(ParamBus bus) {
        this.bus = bus;
    }

    @PostConstruct
    public void startServers() throws InterruptedException {
        startSocks();
        startHttp();
    }

    @PreDestroy
    public void stopServers() {
        System.out.println("Shutting down Netty servers...");
        channels.forEach(Channel::close);
        socksWorkerGroup.shutdownGracefully();
        socksBossGroup.shutdownGracefully();
        httpWorkerGroup.shutdownGracefully();
        httpBossGroup.shutdownGracefully();
    }

    /**
     * Socks5 starter
     */
    private void startSocks() throws InterruptedException {
        ServerBootstrap bs = new ServerBootstrap();
        bs.group(socksBossGroup, socksWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LoggingHandler(LogLevel.INFO),
                                new VersionHandler(bus));
                    }
                });

        Channel channel = bs.bind(bus.params.getLocalPort()).sync().channel();
        channels.add(channel);
        System.out.println("SOCKS5 server started on port " + bus.params.getLocalPort());
    }

    /**
     * HTTP starter
     */
    private void startHttp() throws InterruptedException {
        ServerBootstrap bh = new ServerBootstrap();
        bh.group(httpBossGroup, httpWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(bus.namer.generateName(), new HttpServerCodec())
                                .addLast(bus.namer.generateName(), new FrontHandler(bus));
                    }
                });

        Channel channel = bh.bind(bus.params.getHttpPort()).sync().channel();
        channels.add(channel);
        System.out.println("HTTP server started on port " + bus.params.getHttpPort());
    }
}
