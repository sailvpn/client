package com.illiad.handler.v5;

import com.illiad.codec.HeaderEncoder;

import com.illiad.codec.v5.V5ClientEncoder;
import com.illiad.codec.v5.V5CommandResDecoder;
import com.illiad.config.Params;
import com.illiad.handler.RelayHandler;
import com.illiad.handler.Utils;
import com.illiad.security.Ssl;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public final class V5ConnectHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {

    private final Ssl ssl;
    private final Params params;
    private final HeaderEncoder headerEncoder;
    private final V5ClientEncoder v5ClientEncoder;
    private final Utils utils;
    private final Bootstrap b = new Bootstrap();

    public V5ConnectHandler(Ssl ssl, Params params, HeaderEncoder headerEncoder, V5ClientEncoder v5ClientEncoder, Utils utils) {

        this.ssl = ssl;
        this.params = params;
        this.headerEncoder = headerEncoder;
        this.v5ClientEncoder = v5ClientEncoder;
        this.utils = utils;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final Socks5CommandRequest request) {

        // defind a promise to handle the connection to the remote server
        Promise<Channel> promise = ctx.executor().newPromise();
        promise.addListener(
                (FutureListener<Channel>) future -> {
                    final Channel frontend = ctx.channel();
                    final ChannelPipeline frontendPipeline = ctx.pipeline();
                    final Channel backend = future.getNow();
                    final ChannelPipeline backendPipeline = backend.pipeline();
                    if (future.isSuccess()) {
                        // remove all handlers except SslHandler from backend pipeline
                        for (String name : backendPipeline.names()) {
                            ChannelHandler handler = backendPipeline.get(name);
                            if (!(handler instanceof SslHandler)) {
                                backendPipeline.remove(name);
                            }
                        }

                        // remove all handlers from frontend pipeline
                        for (String name : frontendPipeline.names()) {
                            frontendPipeline.remove(name);
                        }

                        // setup Socks direct channel relay between frontend and backend
                        frontendPipeline.addLast(new RelayHandler(backend, utils));
                        backendPipeline.addLast(new RelayHandler(frontend, utils));
                        // restore frontend auto read
                        ctx.channel().config().setAutoRead(true);
                    } else {
                        utils.closeOnFlush(ctx.channel());
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
                                // backend inbound decoder: standard socks5 command response
                                new V5CommandResDecoder(),
                                new V5AckHandler(promise),
                                // backend outbound encoder: standard socks5 command request (Connect or UdP)
                                v5ClientEncoder,
                                // illiad header
                                headerEncoder);
                    }
                });

        // connect to the proxy server, and forward the Socks connect command message to the remote server
        b.connect(params.getRemoteHost(), params.getRemotePort())
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
