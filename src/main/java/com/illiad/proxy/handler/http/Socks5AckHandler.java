package com.illiad.proxy.handler.http;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.v5.RelayHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.ReferenceCountUtil;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Handles the SOCKS5 command response acknowledgment and sets up the HTTP request forwarding or tunneling.
 */
public class Socks5AckHandler extends SimpleChannelInboundHandler<Socks5CommandResponse> {

    private final ChannelHandlerContext frontendCtx;
    private final ParamBus bus;
    private final HttpRequest initialReq;
    private URI parsed;
    private StringBuffer newUri = ;
    private StringBuffer query;

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
            frontendPipeline.addLast(new RelayHandler(backend, bus.utils));
            backendPipeline.addLast(new RelayHandler(frontend, bus.utils));
            String prefix = bus.namer.getPrefix();
            // backend handlers are socks5-related, must be removed A.S.A.P
            // remove all handlers except SslHandler from backendPipeline
            for (String name : backendPipeline.names()) {
                if (name.startsWith(prefix)) {
                    backendPipeline.remove(name);
                }
            }

            /**
             * frontend handlers are HTTP codecs, and will still be needed in the scenario of HTTP CONNECT,
             */

            // If the original request was a CONNECT, reply 200 to frontend and start tunneling.
            if (initialReq.method().equals(HttpMethod.CONNECT)) {
                // no body, just flush the status then start raw relay (RelayHandler is in place)
                try {
                    frontendCtx.writeAndFlush(new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.valueOf(200, bus.utils.ESTABLISHED)
                    )).sync();
                } catch (InterruptedException e) {
                    frontendCtx.fireExceptionCaught(e);
                }

            } else {

                // For normal HTTP requests: convert proxy-style absolute-URI to origin-form if needed,
                // strip proxy-specific headers, retain and forward the request.
                String uri = initialReq.uri();
                if (uri.startsWith(bus.utils.HTTPS) || uri.startsWith(bus.utils.HTTP)) {
                    try {
                        parsed = new URI(uri);
                        newUri.append(parsed.getRawPath());
                        query.append(parsed.getRawQuery());
                        if (newUri.isEmpty()) {
                            newUri.append(bus.utils.SLASH);
                        }
                        if (!query.isEmpty()) {
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

                // retain before forwarding to avoid premature release (message lifecycle managed by Netty)
                ReferenceCountUtil.retain(initialReq);
                backend.writeAndFlush(initialReq);
            }

            // frontend handlers can now be safely removed
            for (String name : frontendPipeline.names()) {
                if (name.startsWith(prefix)) {
                    frontendPipeline.remove(name);
                }
            }
        } else {
            frontendCtx.writeAndFlush(new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(502, response.status().toString())
            ));
            frontendCtx.fireExceptionCaught(new Exception(response.status().toString()));
            bus.utils.closeOnFlush(frontendCtx.channel());
            bus.utils.closeOnFlush(ctx.channel());
        }

    }
}

