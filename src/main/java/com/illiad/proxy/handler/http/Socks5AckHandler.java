package com.illiad.proxy.handler.http;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.v5.RelayHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.ssl.SslHandler;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Robust SOCKS5 Acknowledgment Handler.
 * Fixes "Connection Reset" by ensuring pipeline atomic transitions.
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
            final Channel backend = ctx.channel();
            final Channel frontend = frontendCtx.channel();
            final String prefix = bus.namer.getPrefix();

            // 1. DYNAMICALLY PAUSE READS
            // This prevents the OS from pumping new bytes (like TLS handshakes)
            // into the pipeline while we are still modifying it.
            frontend.config().setAutoRead(false);
            backend.config().setAutoRead(false);

            // 2. RESTRUCTURE PIPELINES
            // For Backend: Add Relay AFTER SslHandler (residential) to handle decrypted data
            if (backend.pipeline().get(SslHandler.class) != null) {
                backend.pipeline().addAfter(
                        backend.pipeline().context(SslHandler.class).name(),
                        "relay-to-frontend",
                        new RelayHandler(frontend, bus)
                );
            } else {
                backend.pipeline().addFirst("relay-to-frontend", new RelayHandler(frontend, bus));
            }

            // For Frontend: Add Relay at the front (no residential handlers usually)
            frontend.pipeline().addFirst("relay-to-backend", new RelayHandler(backend, bus));

            // 3. REMOVE TRANSIENT HANDLERS (HttpServerCodec, Aggregator, etc.)
            // We do this BEFORE flushing the 200 OK or forwarding the request.
            cleanupPipeline(frontend.pipeline(), prefix);
            cleanupPipeline(backend.pipeline(), prefix);

            // 4. SIGNAL & RESUME
            if (initialReq.method().equals(HttpMethod.CONNECT)) {
                handleHttpsEstablished(frontend, backend);
            } else {
                handleHttpForwarding(frontend, backend);
            }

        } else {
            handleSocksFailure(ctx, response);
        }
    }

    private void handleHttpsEstablished(Channel frontend, Channel backend) {
        // Send '200 Connection Established' to the client
        ByteBuf ok = bus.utils.encodeRes(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(200, bus.utils.ESTABLISHED)));

        frontend.writeAndFlush(ok).addListener(f -> {
            // Re-enable reads once the pipe is clear and the signal is out
            resume(frontend, backend);
        });
    }

    private void handleHttpForwarding(Channel frontend, Channel backend) {
        // Normalize the URI from Absolute to Origin form
        normalizeUri(initialReq);

        // remove proxy headers that should not be forwarded to origin
        HttpHeaders headers = initialReq.headers();
        headers.remove(bus.utils.PROXY_AUTHORIZATION);
        headers.remove(bus.utils.PROXY_AUTHENTICATE);
        headers.remove(bus.utils.PROXY_CONNECTION);
        // keep Host header as-is for origin server

        // Forward the request (Assumes HttpObjectAggregator was used in Starter)
        ByteBuf encodedReq = bus.utils.encodeReq(initialReq);
        backend.writeAndFlush(encodedReq).addListener(f -> resume(frontend, backend));
    }

    private void normalizeUri(HttpRequest req) {
        String uri = req.uri();
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            try {
                URI p = new URI(uri);
                String path = (p.getRawPath() == null || p.getRawPath().isEmpty()) ? "/" : p.getRawPath();
                if (p.getRawQuery() != null) path += "?" + p.getRawQuery();
                req.setUri(path);
            } catch (URISyntaxException ignored) {
            }
        }
    }

    private void cleanupPipeline(ChannelPipeline p, String prefix) {
        // Safe removal loop using your namer convention
        p.names().stream()
                .filter(name -> name.startsWith(prefix))
                .forEach(p::remove);
    }

    private void resume(Channel f, Channel b) {
        f.config().setAutoRead(true);
        b.config().setAutoRead(true);
    }

    private void handleSocksFailure(ChannelHandlerContext ctx, Socks5CommandResponse res) {
        ByteBuf err = bus.utils.encodeRes(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY));
        frontendCtx.writeAndFlush(err).addListener(ChannelFutureListener.CLOSE);
        ctx.close();
    }
}

