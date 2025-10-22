package com.illiad.proxy.handler.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

public class BackHandler extends ChannelInboundHandlerAdapter {

    private final Channel inboundChannel;
    private final HttpRequest initialRequest;

    public BackHandler(Channel inboundChannel, HttpRequest initialRequest) {
        this.inboundChannel = inboundChannel;
        this.initialRequest = initialRequest;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // Once connected to the target via SOCKS5, send the initial request.
        if (initialRequest != null) {
            ctx.writeAndFlush(initialRequest).addListener(future -> {
                if (future.isSuccess()) {
                    // Start reading from the inbound channel again
                    inboundChannel.config().setAutoRead(true);
                }
            });
        } else {
            // For CONNECT requests, send a 200 OK back to the client
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            inboundChannel.writeAndFlush(response);

            // For blind forwarding, remove HTTP codecs and just relay bytes
            inboundChannel.pipeline().remove(HttpServerCodec.class);
            ctx.pipeline().remove(HttpClientCodec.class);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // Forward the message from the target server back to the client.
        if (inboundChannel.isActive()) {
            inboundChannel.writeAndFlush(msg).addListener(future -> {
                if (!future.isSuccess()) {
                    ((ChannelFuture) future).channel().close();
                }
            });
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (inboundChannel.isActive()) {
            inboundChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}

