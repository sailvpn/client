package com.illiad.proxy.handler.http;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.codec.v5.PseudoResDecoder;
import com.illiad.proxy.codec.v5.V5ClientDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.proxy.ProxyConnectException;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ByteProcessor;
import io.netty.util.CharsetUtil;
import lombok.Getter;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class FrontHandler extends ByteToMessageDecoder {

    public enum TunnelState {
        OFF,
        CONNECTING,
        ON
    }

    private static final byte[] TARGET_PATTERN = new byte[]{0x0D, 0x0A, 0x0D, 0x0A}; // "\r\n\r\n"

    private final ParamBus bus;
    private Channel outbound;
    private TunnelState state = TunnelState.OFF;
    @Getter
    private boolean isConnectMethod = false;

    // FIFO queue storing early in-flight bytes arriving while the upstream proxy connects
    private final Queue<ByteBuf> earlyBytesBuffer = new LinkedList<>();

    public FrontHandler(ParamBus bus) {
        this.bus = bus;
    }

    /**
     * Shifts the tunnel state to ON and flushes all cached early byte payloads down the wire.
     * For plaintext requests, this immediately sends the initial GET/POST bytes to the server.
     */
    public void activateTunnel() {
        this.state = TunnelState.ON;
        if (outbound != null && outbound.isActive()) {
            while (!earlyBytesBuffer.isEmpty()) {
                ByteBuf cachedData = earlyBytesBuffer.poll();
                if (cachedData != null) {
                    outbound.writeAndFlush(cachedData);
                }
            }
        } else {
            clearEarlyBytesBuffer();
        }
    }

    private void clearEarlyBytesBuffer() {
        while (!earlyBytesBuffer.isEmpty()) {
            ByteBuf buf = earlyBytesBuffer.poll();
            if (buf != null) {
                buf.release(); // Prevent direct off-heap leaks if handshake falls over
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        clearEarlyBytesBuffer();
        if (outbound != null) {
            outbound.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        clearEarlyBytesBuffer();
        if (outbound != null) {
            outbound.close();
        }
        ctx.close();
    }

    /**
     * Zero-allocation ByteProcessor matching the terminal pattern sequence "\r\n\r\n".
     */
    private static class CrlfSearchProcessor implements ByteProcessor {
        private int matchCount = 0;

        @Override
        public boolean process(byte value) {
            if (value == TARGET_PATTERN[matchCount]) {
                matchCount++;
                if (matchCount == TARGET_PATTERN.length) {
                    return false; // Found the pattern; end the search loop
                }
            } else {
                matchCount = (value == TARGET_PATTERN[0]) ? 1 : 0;
            }
            return true;
        }

        public int getMatchCount() {
            return matchCount;
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // --- STATE 3: TUNNEL IS ACTIVE (ON) ---
        // Permanent frontline routing: instantly forward all raw binary/encrypted frames
        if (state == TunnelState.ON) {
            if (outbound != null && outbound.isActive()) {
                ByteBuf data = in.readBytes(in.readableBytes());
                outbound.writeAndFlush(data);
            } else {
                in.skipBytes(in.readableBytes());
                ctx.fireExceptionCaught(new ProxyConnectException(
                        "backend tunnel went inactive unexpectedly..."
                ));
            }
            return;
        }

        // --- STATE 2: TUNNEL IS CONNECTING ---
        // Safely cache incoming byte frames without altering autoRead state
        if (state == TunnelState.CONNECTING) {
            if (in.readableBytes() > 0) {
                earlyBytesBuffer.offer(in.readBytes(in.readableBytes()).retain());
                ctx.fireChannelReadComplete(); // Keeps Netty's stream allocation engine moving
            }
            return;
        }

        // --- STATE 1: INITIAL HEADER PARSING (OFF) ---
        CrlfSearchProcessor processor = new CrlfSearchProcessor();
        int matchEndIndex = in.forEachByte(in.readerIndex(), in.readableBytes(), processor);

        if (processor.getMatchCount() < TARGET_PATTERN.length) {
            return; // Incomplete boundary marker; let Netty continue to accumulate TCP chunks
        }

        // Calculate total byte boundary size of the incoming HTTP header block
        int totalHeaderLength = (matchEndIndex - in.readerIndex()) + 1;

        // Peek/slice out the header bytes cleanly from the stream without advancing reader index yet
        ByteBuf headerSlice = in.slice(in.readerIndex(), totalHeaderLength);
        String requestString = headerSlice.toString(CharsetUtil.UTF_8);
        String lowerRequest = requestString.toLowerCase(java.util.Locale.ROOT);

        String targetHost = null;
        int port = -1; // Sentinel value to verify valid parsing step boundaries

        try {
            // 1. Robust Case-Insensitive evaluation of "host: "
            int hostIdx = lowerRequest.indexOf("host: ");
            if (hostIdx != -1) {
                int endLineIdx = requestString.indexOf("\r\n", hostIdx);
                if (endLineIdx != -1) {
                    String hostLine = requestString.substring(hostIdx + 6, endLineIdx).trim();
                    if (hostLine.contains(":")) {
                        String[] parts = hostLine.split(":");
                        targetHost = parts[0];
                        port = Integer.parseInt(parts[1]);
                    } else {
                        targetHost = hostLine;
                    }
                }
            }

            // 2. Fallback: Parse the raw HTTP Request-Line directly
            int firstLineEnd = requestString.indexOf("\r\n");
            if (firstLineEnd != -1) {
                String firstLine = requestString.substring(0, firstLineEnd);
                String[] tokens = firstLine.split("\\s+");

                if (tokens.length >= 2) {
                    String method = tokens[0].toUpperCase(java.util.Locale.ROOT);
                    String uri = tokens[1];

                    if ("CONNECT".equals(method)) {
                        if (port == -1) port = 443; // Standard signature tracking profile
                        if (uri.contains(":")) {
                            String[] parts = uri.split(":");
                            targetHost = parts[0];
                            port = Integer.parseInt(parts[1]);
                        } else {
                            targetHost = uri;
                        }
                    } else if (targetHost == null) {
                        // Extract domain names out of absolute paths for standard HTTP requests
                        if (uri.startsWith("http://") || uri.startsWith("https://")) {
                            java.net.URI parsedUri = new java.net.URI(uri);
                            targetHost = parsedUri.getHost();
                            port = parsedUri.getPort();
                        }
                    }
                }
            }

            // Final fallback validation step for default protocol configurations
            if (port == -1) {
                port = lowerRequest.startsWith("connect") ? 443 : 80;
            }

            if (targetHost == null || targetHost.isEmpty() || port < 0 || port > 65535) {
                in.skipBytes(in.readableBytes());
                ctx.close();
                return;
            }

        } catch (Exception e) {
            in.skipBytes(in.readableBytes());
            ctx.close();
            return;
        }

        // --- UNIFIED TUNNEL STATE SHIFT ---
        this.isConnectMethod = lowerRequest.startsWith("connect");
        this.state = TunnelState.CONNECTING;

        if (this.isConnectMethod) {
            // A. HTTPS CONNECT TRACK:
            // 1. Skip over the proxy CONNECT header completely so it never reaches remote TLS engines
            in.skipBytes(totalHeaderLength);

            // 2. Pull out the trailing bytes (the binary TLS Client Hello) and place them FIRST into the FIFO queue
            if (in.readableBytes() > 0) {
                earlyBytesBuffer.offer(in.readBytes(in.readableBytes()).retain());
            }
        } else {
            // B. PLAINTEXT HTTP TRACK (GET/POST/FETCH):
            // 1. Normalize and pack headers into FIFO Position #1
            int firstLineEnd = requestString.indexOf("\r\n");
            String firstLine = requestString.substring(0, firstLineEnd);
            String[] tokens = firstLine.split("\\s+");

            if (tokens.length >= 2 && (tokens[1].startsWith("http://") || tokens[1].startsWith("https://"))) {
                ByteBuf headerBuffer = ctx.alloc().buffer();
                try {
                    // Rewrite absolute path "GET http://sina.com.cn" -> relative path "GET /index.html"
                    java.net.URI parsedUri = new java.net.URI(tokens[1]);
                    String rawPath = parsedUri.getRawPath();
                    String relativePath = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;
                    if (parsedUri.getRawQuery() != null) {
                        relativePath += "?" + parsedUri.getRawQuery();
                    }

                    String newFirstLine = tokens[0] + " " + relativePath + " " + tokens[2] + "\r\n";
                    headerBuffer.writeBytes(newFirstLine.getBytes(java.nio.charset.StandardCharsets.UTF_8));

                    in.skipBytes(firstLineEnd + 2); // Skip the absolute first line from input stream
                    headerBuffer.writeBytes(in.readBytes(totalHeaderLength - (firstLineEnd + 2))); // Copy all other headers

                    earlyBytesBuffer.offer(headerBuffer.retain()); // Headers offered first
                } catch (Exception ex) {
                    headerBuffer.release();
                    in.skipBytes(in.readableBytes());
                    ctx.close();
                    return;
                } finally {
                    headerBuffer.release(); // Balance local instant tracking footprint allocation
                }
            } else {
                // Verbatim safe fallback tracking execution allocation
                earlyBytesBuffer.offer(in.readBytes(totalHeaderLength).retain());
            }

            // 2. Pull out trailing payload bytes (e.g. POST Body details) and place them SECOND into the FIFO queue
            if (in.readableBytes() > 0) {
                earlyBytesBuffer.offer(in.readBytes(in.readableBytes()).retain());
            }
        }

        // 3. Freeze incoming kernel reads until our SOCKS channel establishes its connection handshake
        // ctx.channel().config().setAutoRead(false);

        // --- CASCADING OUTBOUND HANDSHAKE BOOTSTRAP ---
        Socks5CommandRequest socksReq = new DefaultSocks5CommandRequest(
                Socks5CommandType.CONNECT,
                bus.utils.addressType(targetHost),
                targetHost,
                port
        );

        final Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel sc) {
                    }
                });

        b.connect(bus.params.getRemoteHost(), bus.params.getRemotePort())
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        outbound = future.channel();
                        SslHandler sslHandler = bus.cert.sslCtx.newHandler(outbound.alloc(), bus.params.getRemoteHost(), bus.params.getRemotePort());
                        ChannelPipeline pipeline = outbound.pipeline();
                        pipeline.addLast(sslHandler);

                        sslHandler.handshakeFuture().addListener(future1 -> {
                            if (future1.isSuccess()) {
                                pipeline.addFirst(new IdleStateHandler(0, 0, 60), new ChannelDuplexHandler() {
                                            @Override
                                            public void userEventTriggered(ChannelHandlerContext ctx1, Object evt) throws Exception {
                                                if (evt instanceof IdleStateEvent) {
                                                    bus.utils.closeOnFlush(ctx1.channel());
                                                } else {
                                                    super.userEventTriggered(ctx1, evt);
                                                }
                                            }
                                        })
                                        .addLast(new PseudoResDecoder())
                                        .addLast(bus.namer.generateName(), bus.v5ClientEncoder)
                                        .addLast(bus.namer.generateName(), new V5ClientDecoder(bus))
                                        .addLast(bus.namer.generateName(), new Socks5AckHandler(ctx, bus))
                                        .channel()
                                        .writeAndFlush(socksReq).addListener((ChannelFutureListener) future2 -> {
                                            if (!future2.isSuccess()) {
                                                clearEarlyBytesBuffer();
                                                bus.utils.closeOnFlush(outbound);
                                                bus.utils.closeOnFlush(ctx.channel());
                                            }
                                        });
                            } else {
                                clearEarlyBytesBuffer();
                                bus.utils.closeOnFlush(outbound);
                                bus.utils.closeOnFlush(ctx.channel());
                            }
                        });
                    } else {
                        clearEarlyBytesBuffer();
                        bus.utils.closeOnFlush(ctx.channel());
                    }
                });
    }

}

