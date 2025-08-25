package com.illiad.proxy.handler.v5;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.codec.v5.V5ClientDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.ssl.SslHandler;

public class V5ConnectHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {

    private final ParamBus bus;
    private final Bootstrap b = new Bootstrap();

    public V5ConnectHandler(ParamBus bus) {
        this.bus = bus;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final Socks5CommandRequest request) {

        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel sc) {
                    }
                })
                // connect to the proxy server, and forward the Socks connect command message to the remote server
                .connect(bus.params.getRemoteHost(), bus.params.getRemotePort())
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        Channel ch = future.channel();
                        SslHandler sslHandler = bus.ssl.sslCtx.newHandler(ch.alloc(), bus.params.getRemoteHost(), bus.params.getRemotePort());
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(sslHandler);
                        // Add a listener for the SSL handshake
                        sslHandler.handshakeFuture().addListener(future1 -> {
                            if (future1.isSuccess()) {
                                // backend outbound encoder: standard socks5 command request (Connect)
                                pipeline.addLast(bus.namer.generateName(), bus.v5ClientEncoder)
                                        // backend inbound decoder: socks5 client decoder
                                        .addLast(bus.namer.generateName(), new V5ClientDecoder(bus))
                                        .addLast(bus.namer.generateName(), new V5AckHandler(ctx, bus))
                                        .channel()
                                        .writeAndFlush(request).addListener((ChannelFutureListener) future2 -> {
                                            if (!future2.isSuccess()) {
                                                ctx.fireExceptionCaught(new Exception(future2.cause()));
                                                bus.utils.closeOnFlush(ch);
                                                bus.utils.closeOnFlush(ctx.channel());
                                            }
                                        });
                            } else {
                                ctx.fireExceptionCaught(new Exception(future1.cause()));
                                bus.utils.closeOnFlush(ch);
                                bus.utils.closeOnFlush(ctx.channel()); // Close the channel on failure
                            }
                        });
                    } else {
                        // Close the connection if the connection attempt has failed.
                        ctx.fireExceptionCaught(future.cause());
                        bus.utils.closeOnFlush(future.channel());
                        bus.utils.closeOnFlush(ctx.channel());
                    }
                });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        bus.utils.closeOnFlush(ctx.channel());
        ctx.fireExceptionCaught(cause);
    }
}
