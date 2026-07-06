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

    private static final byte[] TARGET_PATTERN = new byte[]{ 0x0D, 0x0A, 0x0D, 0x0A }; // "\r\n\r\n"

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

        public int getMatchCount() { return matchCount; }
    }

    // CONTINUATION OF FRONTHANDLER CLASS FILE BODY (PART 2):

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
                // Throwing an exception triggers your local exceptionCaught() block down the line,
                // which handles closing the channels and frees off-heap buffers cleanly.
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
                earlyBytesBuffer.offer(in.readBytes(in.readableBytes()));
            }
            return;
        }

        // --- STATE 1: INITIAL HEADER PARSING (OFF) ---
        CrlfSearchProcessor processor = new CrlfSearchProcessor();
        int matchEndIndex = in.forEachByte(in.readerIndex(), in.readableBytes(), processor);

        if (processor.getMatchCount() < TARGET_PATTERN.length) {
            return; // Incomplete framework boundary marker; let Netty continue to accumulate TCP chunks
        }

        int totalHeaderLength = (matchEndIndex - in.readerIndex()) + 1;

        // Peek/slice out the header bytes cleanly from the stream without advancing reader index yet.
        // For plaintext HTTP (GET/POST), we MUST forward the initial request down the tunnel later!
        ByteBuf headerSlice = in.slice(in.readerIndex(), totalHeaderLength);
        String requestString = headerSlice.toString(CharsetUtil.UTF_8);

        String targetHost = null;
        int port = 80; // Default to 80 for plaintext HTTP, will override to 443 if CONNECT is detected

        try {
            // 1. Look for the standard "Host: " header (Reliable for GET, POST, FETCH, and CONNECT)
            int hostIdx = requestString.indexOf("Host: ");
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
                        // Set standard port footprint based on request signature if missing
                        port = requestString.startsWith("CONNECT") ? 443 : 80;
                    }
                }
            }

            // 2. Fallback: Parse the first line directly if "Host: " header parsing missed
            if (targetHost == null) {
                int connectIdx = requestString.indexOf("CONNECT ");
                if (connectIdx != -1) {
                    port = 443; // Explicit HTTPS tunnel signature
                    int spaceIdx = requestString.indexOf(" ", connectIdx + 8);
                    if (spaceIdx != -1) {
                        String hostPortPart = requestString.substring(connectIdx + 8, spaceIdx).trim();
                        if (hostPortPart.contains(":")) {
                            String[] parts = hostPortPart.split(":");
                            targetHost = parts[0];
                            port = Integer.parseInt(parts[1]);
                        } else {
                            targetHost = hostPortPart;
                        }
                    }
                }
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
        this.isConnectMethod = requestString.startsWith("CONNECT");
        this.state = TunnelState.CONNECTING;

        // Read ALL current bytes out of the stream accumulator (including the header we just parsed)
        // and cache them in the FIFO queue so they can be flushed to the target later.
        earlyBytesBuffer.offer(in.readBytes(in.readableBytes()));

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
                    protected void initChannel(SocketChannel sc) {}
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
                        bus.utils.closeOnFlush(future.channel());
                        bus.utils.closeOnFlush(ctx.channel());
                    }
                });
    }
}
