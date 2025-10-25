package com.illiad.proxy.handler.http;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.codec.v5.V5ClientDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;

public class FrontHandler extends ChannelInboundHandlerAdapter {

    private final ParamBus bus;
    private final Bootstrap b = new Bootstrap();
    private Channel outbound;

    public FrontHandler(ParamBus bus) {
        this.bus = bus;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {

        if (msg instanceof HttpRequest httpReq) {

            // Extract destination from the 'Host' header
            String host = httpReq.headers().get(HttpHeaderNames.HOST);
            if (host == null) {
                ctx.close();
                return;
            }

            int port = httpReq.protocolVersion().equals(HttpVersion.HTTP_1_1) ? 80 : 443;
            String[] hostParts = host.split(":");
            String targetHost = hostParts[0];
            if (hostParts.length > 1) {
                port = Integer.parseInt(hostParts[1]);
            }

            Socks5CommandRequest socksReq = new DefaultSocks5CommandRequest(
                    Socks5CommandType.CONNECT,
                    bus.utils.addressType(targetHost),
                    targetHost,
                    port
            );

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
                            outbound = future.channel();
                            SslHandler sslHandler = bus.ssl.sslCtx.newHandler(outbound.alloc(), bus.params.getRemoteHost(), bus.params.getRemotePort());
                            ChannelPipeline pipeline = outbound.pipeline();
                            pipeline.addLast(sslHandler);
                            // Add a listener for the SSL handshake
                            sslHandler.handshakeFuture().addListener(future1 -> {
                                if (future1.isSuccess()) {
                                    // backend outbound encoder: standard socks5 command request (Connect)
                                    pipeline.addLast(bus.namer.generateName(), bus.v5ClientEncoder)
                                            // backend inbound decoder: socks5 client decoder
                                            .addLast(bus.namer.generateName(), new V5ClientDecoder(bus))
                                            .addLast(bus.namer.generateName(), new Socks5AckHandler(ctx, bus, httpReq))
                                            .channel()
                                            .writeAndFlush(socksReq).addListener((ChannelFutureListener) future2 -> {
                                                if (!future2.isSuccess()) {
                                                    ctx.fireExceptionCaught(new Exception(future2.cause()));
                                                    bus.utils.closeOnFlush(outbound);
                                                    bus.utils.closeOnFlush(ctx.channel());
                                                }
                                            });
                                } else {
                                    ctx.fireExceptionCaught(new Exception(future1.cause()));
                                    bus.utils.closeOnFlush(outbound);
                                    bus.utils.closeOnFlush(ctx.channel()); // Close the channel on failure
                                }
                            });
                        } else {
                            // Close the connection if the connection attempt has failed.
                            ctx.fireExceptionCaught(future.cause());
                            // outboundChannel is null here
                            bus.utils.closeOnFlush(future.channel());
                            bus.utils.closeOnFlush(ctx.channel());
                        }
                    });

        } else {
            ReferenceCountUtil.release(msg);
        }

    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outbound != null) {
            outbound.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.fireExceptionCaught(cause);
        if (outbound != null) {
            outbound.close();
        }
        ctx.close();
    }
}

