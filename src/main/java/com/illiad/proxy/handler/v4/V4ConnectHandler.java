package com.illiad.proxy.handler.v4;

import com.illiad.proxy.codec.HeaderEncoder;
import com.illiad.proxy.codec.v4.V4ClientDecoder;
import com.illiad.proxy.codec.v4.V4ClientEncoder;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.handler.RelayHandler;
import com.illiad.proxy.handler.Utils;
import com.illiad.proxy.security.Ssl;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public final class V4ConnectHandler extends SimpleChannelInboundHandler<Socks4CommandRequest> {

    private final Ssl ssl;
    private final Params params;
    private final HeaderEncoder headerEncoder;
    private final V4ClientEncoder v4ClientEncoder;
    private final Utils utils;
    private final Bootstrap b = new Bootstrap();

    public V4ConnectHandler(Ssl ssl, Params params, HeaderEncoder headerEncoder, V4ClientEncoder v4ClientEncoder, Utils utils) {

        this.ssl = ssl;
        this.params = params;
        this.headerEncoder = headerEncoder;
        this.v4ClientEncoder = v4ClientEncoder;
        this.utils = utils;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final Socks4CommandRequest request) {

        // define a promise to handle the connection to the remote server
        Promise<Channel> promise = ctx.executor().newPromise();
        promise.addListener(
                (FutureListener<Channel>) future -> {
                    final Channel frontend = ctx.channel();
                    final ChannelPipeline frontendPipeline = ctx.pipeline();
                    final Channel backend = future.getNow();
                    final ChannelPipeline backendPipeline = backend.pipeline();
                    if (future.isSuccess()) {
                        // remove all handlers except SslHandler from backendPipeline
                        for (String name : backendPipeline.names()) {
                            ChannelHandler handler = backendPipeline.get(name);
                            if (handler instanceof SslHandler) {
                                continue;
                            }
                            backendPipeline.remove(name);
                        }

                        // remove all handlers except LoggingHandler, V4ConnectHandler from frontendPipeline
                        for (String name : frontendPipeline.names()) {
                            ChannelHandler handler = backendPipeline.get(name);
                            if (handler instanceof LoggingHandler || handler instanceof V4ConnectHandler) {
                                continue;
                            }
                            frontendPipeline.remove(name);
                        }

                        // setup Socks direct channel relay between frontend and backend
                        frontendPipeline.addLast(new RelayHandler(backend, utils));
                        backendPipeline.addLast(new RelayHandler(frontend, utils));
                        // restore frontend auto read
                        frontend.config().setAutoRead(true);
                        frontendPipeline.remove(this);
                    } else {
                        utils.closeOnFlush(frontend);
                        ctx.fireExceptionCaught(future.cause());
                    }
                }
        );

        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        // Add SSL handler first to encrypt and decrypt everything.
                        // In this example, we use a bogus certificate in the server side
                        // and accept any invalid certificates in the client side.
                        // You will need something more complicated to identify both
                        // and server in the real world.
                        pipeline.addLast(ssl.sslCtx.newHandler(ch.alloc(), params.getRemoteHost(), params.getRemotePort()),
                                // backend inbound decoder: standard socks4 command response
                                new V4ClientDecoder(),
                                new V4AckHandler(promise),
                                // backend outbound encoder
                                v4ClientEncoder,
                                // backend header encoder
                                headerEncoder);
                    }
                });

        // connect to the proxy server, and forward the Socks connect command message to the remote server
        b.connect(params.getRemoteHost(), params.getRemotePort()).channel().writeAndFlush(request)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        // Connection established use handler provided results
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
