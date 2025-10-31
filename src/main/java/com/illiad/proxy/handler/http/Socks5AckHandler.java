package com.illiad.proxy.handler.http;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.v5.RelayHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Handles the SOCKS5 command response acknowledgment and sets up the HTTP request forwarding or tunneling.
 */
public class Socks5AckHandler extends SimpleChannelInboundHandler<Socks5CommandResponse> {

    private final ChannelHandlerContext frontendCtx;
    private final ParamBus bus;
    private final HttpRequest initialReq;

    public Socks5AckHandler(ChannelHandlerContext frontendCtx, ParamBus bus, HttpRequest initialReq) {
        this.frontendCtx = frontendCtx;
        this.bus = bus;
        this.initialReq = initialReq;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5CommandResponse response) {
        if (response.status() == Socks5CommandStatus.SUCCESS) {

            Channel frontend = frontendCtx.channel();
            final ChannelPipeline frontendPipeline = frontend.pipeline();
            final Channel backend = ctx.channel();
            final ChannelPipeline backendPipeline = backend.pipeline();
            // setup Socks direct channel relay between frontend and backend
            frontendPipeline.addLast(new RelayHandler(backend, bus));
            backendPipeline.addLast(new RelayHandler(frontend, bus));
            String prefix = bus.namer.getPrefix();
            // remove all handlers except SslHandler, RelayHandler from backendPipeline
            for (String name : backendPipeline.names()) {
                if (name.startsWith(prefix)) {
                    backendPipeline.remove(name);
                }
            }

            // remove all handlers, except RelayHandler from frontendPipeline
            for (String name : frontendPipeline.names()) {
                if (name.startsWith(prefix)) {
                    frontendPipeline.remove(name);
                }
            }

            // If the original request was a CONNECT.
            if (initialReq.method().equals(HttpMethod.CONNECT)) {
                // no body, just flush the status then start raw relay (RelayHandler is in place)
                // send 200 OK to frontend
                ByteBuf responseBuf = bus.utils.encodeRes(
                        new DefaultFullHttpResponse(
                                HttpVersion.HTTP_1_1,
                                HttpResponseStatus.valueOf(200, bus.utils.ESTABLISHED)
                        ));
                frontendCtx.writeAndFlush(responseBuf);

            } else {

                // For normal HTTP requests: convert proxy-style absolute-URI to origin-form if needed,
                // strip proxy-specific headers, and forward the request.
                String uri = initialReq.uri();
                if (uri.startsWith(bus.utils.HTTPS) || uri.startsWith(bus.utils.HTTP)) {
                    try {
                        URI parsed = new URI(uri);
                        StringBuffer newUri = new StringBuffer(parsed.getRawPath());
                        if (newUri.isEmpty()) {
                            newUri.append(bus.utils.SLASH);
                        }
                        String query = parsed.getRawQuery();
                        if (query != null) {
                            newUri.append(bus.utils.QUESTION).append(query);
                        }
                        // setUri is available on Netty's HttpRequest implementations
                        initialReq.setUri(newUri.toString());
                    } catch (URISyntaxException ignore) {
                        // leave original uri if parsing fails
                    }
                }

                // remove proxy headers that should not be forwarded to origin
                HttpHeaders headers = initialReq.headers();
                headers.remove(bus.utils.PROXY_AUTHORIZATION);
                headers.remove(bus.utils.PROXY_AUTHENTICATE);
                headers.remove(bus.utils.PROXY_CONNECTION);
                // keep Host header as-is for origin server

                ByteBuf encodedReq = bus.utils.encodeReq(initialReq);
                backend.writeAndFlush(encodedReq);
            }

        } else {
            // send error response to frontend
            ByteBuf errorBuf = bus.utils.encodeRes(
                    new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.valueOf(502, response.status().toString())
                    ));
            frontendCtx.writeAndFlush(errorBuf);
            frontendCtx.fireExceptionCaught(new Exception(response.status().toString()));
            bus.utils.closeOnFlush(frontendCtx.channel());
            bus.utils.closeOnFlush(ctx.channel());
        }

    }
}

