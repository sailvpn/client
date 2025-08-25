package com.illiad.proxy.handler.v5;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.v5.udp.Aso;
import com.illiad.proxy.handler.v5.udp.UdpRelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;

import java.net.InetSocketAddress;

public class AssociateHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {

    private final ParamBus bus;

    public AssociateHandler(ParamBus bus) {
        this.bus = bus;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final Socks5CommandRequest request) {

        // Create a new UDP relay channel
        EventLoopGroup group = new NioEventLoopGroup(2);
        Bootstrap udpBootstrap = new Bootstrap();
        udpBootstrap.group(group)
                .channel(NioDatagramChannel.class)
                // Enable broadcasting if needed
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) {
                        ch.pipeline().addLast(new UdpRelayHandler(bus));
                    }
                })
                .bind(bus.params.getUdpHost(), 0)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        Channel bind = future.channel();
                        // Register the new UDP associate-bind-remote_address(IP) association in the binds list
                        bus.asos.addAso(new Aso(ctx.channel(), bind));
                        InetSocketAddress localAddr = (InetSocketAddress) bind.localAddress();
                        String host = localAddr.getHostString();
                        // Build a successful UDP_ASSOCIATE response
                        Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                                Socks5CommandStatus.SUCCESS,
                                bus.utils.addressType(host),
                                host,
                                ((InetSocketAddress) future.channel().localAddress()).getPort()
                        );
                        // Write the response to the client
                        ctx.channel().writeAndFlush(response);
                        ctx.pipeline().addLast(new CloseHandler(bus));
                        ctx.pipeline().remove(this);
                        // remove all handlers except , SslHandler from frontendPipeline
                        String prefix = bus.namer.getPrefix();
                        ChannelPipeline pipeline = ctx.pipeline();
                        for (String name : pipeline.names()) {
                            if (name.startsWith(prefix)) {
                                pipeline.remove(name);
                            }
                        }
                    }
                });
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        bus.utils.closeOnFlush(ctx.channel());
        ctx.fireExceptionCaught(cause);
    }
}