package com.illiad.proxy.handler.v5;

import com.illiad.proxy.HandlerNamer;
import com.illiad.proxy.codec.v5.V5AddressDecoder;
import com.illiad.proxy.codec.v5.V5ClientDecoder;
import com.illiad.proxy.codec.v5.V5ClientEncoder;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.handler.RelayHandler;
import com.illiad.proxy.handler.Utils;
import com.illiad.proxy.security.Ssl;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public class V5ConnectHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {

    private final Ssl ssl;
    private final Params params;
    private final HandlerNamer namer;
    private final V5ClientEncoder v5ClientEncoder;
    private final V5AddressDecoder v5AddressDecoder;
    private final Utils utils;
    private final Bootstrap b = new Bootstrap();

    public V5ConnectHandler(Ssl ssl, Params params, HandlerNamer namer, V5ClientEncoder v5ClientEncoder, V5AddressDecoder v5AddressDecoder, Utils utils) {

        this.ssl = ssl;
        this.params = params;
        this.namer = namer;
        this.v5ClientEncoder = v5ClientEncoder;
        this.v5AddressDecoder = v5AddressDecoder;
        this.utils = utils;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final Socks5CommandRequest request) {

        // define a promise to handle the connection to the remote server
        Promise<Channel> promise = ctx.executor().newPromise();
        promise.addListener((FutureListener<Channel>) future -> {
            if (future.isSuccess()) {
                final Channel frontend = ctx.channel();
                final ChannelPipeline frontendPipeline = ctx.pipeline();
                final Channel backend = future.get();
                final ChannelPipeline backendPipeline = backend.pipeline();
                // setup Socks direct channel relay between frontend and backend
                frontendPipeline.addLast(new RelayHandler(backend, utils));
                backendPipeline.addLast(new RelayHandler(frontend, utils));
                String prefix = namer.getPrefix();
                // remove all handlers except SslHandler from backendPipeline
                for (String name : backendPipeline.names()) {
                    if (name.startsWith(prefix)) {
                        backendPipeline.remove(name);
                    }
                }
                // remove all handlers except LoggingHandler from frontendPipeline
                for (String name : frontendPipeline.names()) {
                    if (name.startsWith(prefix)) {
                        frontendPipeline.remove(name);
                    }
                }
            } else {
                utils.closeOnFlush(ctx.channel());
                ctx.fireExceptionCaught(future.cause());
            }
        });

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
                .connect(params.getRemoteHost(), params.getRemotePort())
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        Channel ch = future.channel();
                        SslHandler sslHandler = ssl.sslCtx.newHandler(ch.alloc(), params.getRemoteHost(), params.getRemotePort());
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(sslHandler);
                        // Add a listener for the SSL handshake
                        sslHandler.handshakeFuture().addListener(future1 -> {
                            if (future1.isSuccess()) {
                                // backend outbound encoder: standard socks5 command request (Connect or UdP)
                                pipeline.addLast(namer.generateName(), v5ClientEncoder)
                                        // backend inbound decoder: socks5 client decoder
                                        .addLast(namer.generateName(), new V5ClientDecoder(v5AddressDecoder))
                                        .addLast(namer.generateName(), new V5AckHandler(ctx.channel(), promise))
                                        .channel()
                                        .writeAndFlush(request).addListener((ChannelFutureListener) future2 -> {
                                            if (!future2.isSuccess()) {
                                                System.err.println("Failed to write request: " + future2.cause());
                                                ctx.channel().close();
                                                utils.closeOnFlush(ch);
                                            }
                                        });
                            } else {
                                System.err.println("SSL Handshake failed: " + future1.cause());
                                ch.close();
                                ctx.channel().close(); // Close the channel on failure
                            }
                        });
                    } else {
                        // Close the connection if the connection attempt has failed.
                        utils.closeOnFlush(ctx.channel());
                        ctx.fireExceptionCaught(future.cause());
                    }
                });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        utils.closeOnFlush(ctx.channel());
        ctx.fireExceptionCaught(cause);
    }
}
