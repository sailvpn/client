package com.illiad.proxy.handler.http;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;

public class FrontHandler extends ChannelInboundHandlerAdapter {

    private final String socks5ProxyHost;
    private final int socks5ProxyPort;
    private volatile Channel outboundChannel;
    private HttpRequest originalRequest;

    public FrontHandler(String socks5ProxyHost, int socks5ProxyPort) {
        this.socks5ProxyHost = socks5ProxyHost;
        this.socks5ProxyPort = socks5ProxyPort;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        if (outboundChannel != null && outboundChannel.isActive()) {
            // Forward subsequent parts of the request (e.g., body chunks)
            outboundChannel.writeAndFlush(msg);
            return;
        }

        if (!(msg instanceof HttpRequest)) {
            // We should only receive the full HttpRequest on the first read
            ReferenceCountUtil.release(msg);
            return;
        }

        this.originalRequest = (HttpRequest) msg;

        // Extract destination from the 'Host' header
        String host = originalRequest.headers().get(HttpHeaderNames.HOST);
        if (host == null) {
            ctx.close();
            return;
        }

        int port = originalRequest.protocolVersion().equals(HttpVersion.HTTP_1_1) ? 80 : 443;
        String[] hostParts = host.split(":");
        String targetHost = hostParts[0];
        if (hostParts.length > 1) {
            port = Integer.parseInt(hostParts[1]);
        }

        // Handle CONNECT requests for HTTPS traffic
        if (originalRequest.method().equals(HttpMethod.CONNECT)) {
            // Special handling for CONNECT: establish the SOCKS5 tunnel first
            connectToSocks5AndTunnel(ctx, targetHost, port, null);
        } else {
            // Normal HTTP request
            connectToSocks5AndTunnel(ctx, targetHost, port, originalRequest);
        }
    }

    private void connectToSocks5AndTunnel(final ChannelHandlerContext ctx, String targetHost, int targetPort, HttpRequest initialRequest) {
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .option(ChannelOption.AUTO_READ, false)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(
                                // Add the Socks5ProxyHandler to establish the connection via SOCKS5
                                new Socks5ProxyHandler(new InetSocketAddress(socks5ProxyHost, socks5ProxyPort)),
                                // Add the backend handler for bidirectional forwarding
                                new BackHandler(ctx.channel(), initialRequest));
                    }
                });

        ChannelFuture f = b.connect(socks5ProxyHost, socks5ProxyPort);
        outboundChannel = f.channel();

        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                ctx.channel().config().setAutoRead(true);
            } else {
                // Connection to SOCKS5 proxy failed
                future.channel().close();
                ctx.channel().close();
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            outboundChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (outboundChannel != null) {
            outboundChannel.close();
        }
        ctx.close();
    }
}

