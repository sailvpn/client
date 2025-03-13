package com.illiad.handler;

import com.illiad.codec.HeaderEncoder;

import com.illiad.config.Params;
import com.illiad.security.Ssl;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.Socks4ClientDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public final class ConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {

    private final Ssl ssl;
    private final Params params;
    private final HeaderEncoder headerEncoder;
    private final Utils utils;

    public ConnectHandler(Ssl ssl, Params params, HeaderEncoder headerEncoder, Utils utils) {

        this.ssl = ssl;
        this.params = params;
        this.headerEncoder = headerEncoder;
        this.utils = utils;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) {

        // defind a promise to handle the connection to the remote server
        Promise<Channel> promise = ctx.executor().newPromise();
        promise.addListener(
                new FutureListener<>() {
                    @Override
                    public void operationComplete(Future<Channel> future) {
                        final Channel frontend = ctx.channel();
                        final ChannelPipeline frontendPipeline = ctx.pipeline();
                        final Channel backend = future.getNow();
                        final ChannelPipeline backendPipeline = backend.pipeline();
                        if (future.isSuccess()) {
                            // remove all handlers except SslHandler from backpipeline
                            for (String name : backendPipeline.names()) {
                                ChannelHandler handler = backendPipeline.get(name);
                                if (!(handler instanceof SslHandler)) {
                                    backendPipeline.remove(name);
                                }
                            }

                            // remove all handlers from frontpipeline
                            for (String name : frontendPipeline.names()) {
                                frontendPipeline.remove(name);
                            }

                            // setup Socks direct channel relay between frontend and backend
                            frontendPipeline.addLast(new RelayHandler(backend, utils));
                            backendPipeline.addLast(new RelayHandler(frontend, utils));
                            // restore frontend auto read
                            ctx.channel().config().setAutoRead(true);
                            ctx.fireChannelRead(message);
                        } else {
                            utils.closeOnFlush(ctx.channel());
                            ctx.fireExceptionCaught(future.cause());
                        }
                    }
                }
        );

        try {
            Bootstrap b = new Bootstrap();
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
                                    // backend inbound decoder: standard socks5 command response or socks4 command response
                                    message instanceof Socks5CommandRequest ? new Socks5CommandResponseDecoder() : new Socks4ClientDecoder(),
                                    new ResponseHandler(promise),
                                    // backend outbound encoder: a header followeed by standard socks5 command request (Connect or UdP) or sock
                                    message instanceof Socks5CommandRequest ? Socks5ClientEncoder.DEFAULT : Socks4ClientEncoder.INSTANCE,
                                    headerEncoder);
                        }
                    });

            // connect to the proxy server, and forward the Socks connect command message to the remote server
            b.connect(params.getRemoteHost(), params.getRemotePort())
                    .sync().channel()
                    .writeAndFlush(message)
                    .sync();

            ctx.pipeline().remove(this);
        } catch (Exception e) {
            ctx.fireExceptionCaught(e);
        } finally {
            utils.closeOnFlush(ctx.channel());
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        utils.closeOnFlush(ctx.channel());
        ctx.fireExceptionCaught(cause);
    }
}
