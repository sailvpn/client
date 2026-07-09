package com.illiad.proxy.handler.v5.associate;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.udp.UdpRelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.InetSocketAddress;

/**
 * UDP_ASSOCIATE handler on the client side
 */
public class AssociateHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {

    private final ParamBus bus;

    public AssociateHandler(ParamBus bus) {
        this.bus = bus;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final Socks5CommandRequest request) {

        // Get the local IP address the client connected to on the TCP control channel
        String serverIp = ((InetSocketAddress) ctx.channel().localAddress()).getAddress().getHostAddress();
        Bootstrap udpBootstrap = new Bootstrap();

        udpBootstrap.group(ctx.channel().eventLoop())
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) {
                        ch.pipeline().addLast(new UdpRelayHandler(bus));
                    }
                })
                .bind(serverIp, bus.utils.IPV4_ZERO_PORT)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        Channel bind = future.channel();

                        // Register the session to your high-performance thread-safe index
                        if (!bus.asos.initAso(ctx.channel(), bind)) {
                            ctx.fireExceptionCaught(new RuntimeException("Failed to initialize Aso"));
                        }
                        InetSocketAddress localAddr = (InetSocketAddress) bind.localAddress();
                        String host = localAddr.getHostString();

                        // Build compliant SOCKS5 success response
                        Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                                Socks5CommandStatus.SUCCESS,
                                bus.utils.addressType(host),
                                host,
                                localAddr.getPort()
                        );

                        // Send success status back down the client TCP link
                        ctx.channel().writeAndFlush(response);

                        ChannelPipeline pipeline = ctx.pipeline();
                        pipeline.addLast(new IdleStateHandler(0, 0, 60), new ChannelDuplexHandler() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if (evt instanceof IdleStateEvent) {
                                    // No traffic detected for 60 seconds, close the hung proxy tunnel
                                    bus.asos.removeAsobyAssociate(ctx.channel());
                                } else {
                                    super.userEventTriggered(ctx, evt);
                                }
                            }

                            /**
                             * CAUTION: implementing channelInactive() somehow caused the "UdpRelayHandler" to be
                             * removed from binding UDP channel
                             */
                        });

                        // FIX: Loop first to remove SOCKS5 protocol codecs while preserving Idle/Timeout handlers
                        String prefix = bus.namer.getPrefix();
                        for (String name : pipeline.names()) {
                            if (name.startsWith(prefix)) {
                                pipeline.remove(name);
                            }
                        }
                        pipeline.remove(this);

                    } else {
                        ctx.fireExceptionCaught(new Exception(bus.utils.associateFailed, future.cause()));
                    }
                });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        bus.utils.closeOnFlush(ctx.channel());
        ctx.fireExceptionCaught(cause);
    }
}
