package com.illiad.proxy.handler.dtls;

// DtlsHandler.java

import com.illiad.proxy.ParamBus;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * A Netty handler that manages DTLS communication over UDP using SSLEngine.
 * It handles the DTLS handshake and encrypts/decrypts data packets.
 */
public class DtlsHandler extends ChannelDuplexHandler {
    private final ParamBus bus;
    private final ChannelPromise handshakeFuture;
    private final SSLEngine sslEngine;
    private boolean handshakeComplete = false;

    public DtlsHandler(ParamBus bus, InetSocketAddress remoteAddress, ChannelPromise handshakeFuture) {
        this.bus = bus;
        this.handshakeFuture = handshakeFuture;
        this.sslEngine = bus.dtls.sslCtx.createSSLEngine(remoteAddress.getHostString(), remoteAddress.getPort());
        this.sslEngine.setUseClientMode(true);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Start DTLS handshake
        sslEngine.beginHandshake();
        sendHandshake(ctx);
    }

    private void sendHandshake(ChannelHandlerContext ctx) throws Exception {
        // app is empty for initial handshake
        ByteBuffer app = ByteBuffer.allocate(0);
        ByteBuffer net = ByteBuffer.allocate(bus.utils.DTLS_ENCRYPTION_OVERHEAD);
        SSLEngineResult.Status status = sslEngine.wrap(app, net).getStatus();
        if (status == SSLEngineResult.Status.OK) {
            net.flip();
            if (net.hasRemaining()) {
                ByteBuf buf = Unpooled.wrappedBuffer(net);
                ctx.writeAndFlush(new DatagramPacket(buf, ((InetSocketAddress) ctx.channel().remoteAddress())));
            }
        } else {
            ctx.fireExceptionCaught(new Exception(status.name()));
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (ctx != null && msg instanceof DatagramPacket packet) {
            if (handshakeComplete) {
                processData(ctx, packet);
            } else {
                doHandshake(ctx, packet);
            }
        }
    }

    private void processData(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        ByteBuf buf = packet.content();
        // decrypted data can not be larger than paylod
        ByteBuffer app = ByteBuffer.allocate(buf.readableBytes());
        SSLEngineResult.Status status = sslEngine.unwrap(buf.nioBuffer(), app).getStatus();
        if (status == SSLEngineResult.Status.OK) {
            app.flip();
            ByteBuf plainBuf = Unpooled.wrappedBuffer(app);
            // Process decrypted data in appIn
            ctx.fireChannelRead(new DatagramPacket(plainBuf, packet.recipient(), packet.sender()));
        } else if (status == SSLEngineResult.Status.CLOSED) {
            ctx.fireExceptionCaught(new Exception(status.name()));
            ctx.close();
            return;
        } else {
            ctx.fireExceptionCaught(new Exception(status.name()));
            return; // Wait for more data
        }
    }

    private void doHandshake(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {

        SSLEngineResult.HandshakeStatus hsStatus = sslEngine.getHandshakeStatus();
        while (!handshakeComplete) {
            switch (hsStatus) {
                case NEED_UNWRAP:
                case NEED_UNWRAP_AGAIN:
                    ByteBuf buf = packet.content();
                    // no need to keep decrypted data,  can not be larger than paylod
                    SSLEngineResult unwrap = sslEngine.unwrap(buf.nioBuffer(), ByteBuffer.allocate(buf.readableBytes()));
                    SSLEngineResult.Status nwStatus = unwrap.getStatus();
                    if (nwStatus == SSLEngineResult.Status.OK) {
                        hsStatus = unwrap.getHandshakeStatus();
                    } else if (nwStatus == SSLEngineResult.Status.CLOSED) {
                        handshakeFuture.setFailure(new Exception(nwStatus.name()));
                        ctx.fireExceptionCaught(new Exception(nwStatus.name()));
                        ctx.close();
                        return;
                    } else {
                        handshakeFuture.setFailure(new Exception(nwStatus.name()));
                        ctx.fireExceptionCaught(new Exception(nwStatus.name()));
                        return; // Wait for more data
                    }
                    break;
                case NEED_WRAP:
                    ByteBuffer net = ByteBuffer.allocate(bus.utils.HI.length + bus.utils.DTLS_ENCRYPTION_OVERHEAD);
                    SSLEngineResult wrap = sslEngine.wrap(ByteBuffer.wrap(bus.utils.HI), net);
                    SSLEngineResult.Status wStatus = wrap.getStatus();
                    if (wStatus != SSLEngineResult.Status.OK) {
                        hsStatus = wrap.getHandshakeStatus();
                        net.flip();
                        // packet.sender become the recipient, packet.recipient become the sender
                        ctx.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(net), packet.sender(), packet.recipient()));
                    } else if (wStatus == SSLEngineResult.Status.CLOSED) {
                        handshakeFuture.setFailure(new Exception(wStatus.name()));
                        ctx.fireExceptionCaught(new Exception(wStatus.name()));
                        ctx.close();
                        return;
                    } else {
                        handshakeFuture.setFailure(new Exception(wStatus.name()));
                        ctx.fireExceptionCaught(new Exception(wStatus.name()));
                        return; // Wait for more data
                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    hsStatus = sslEngine.getHandshakeStatus();
                    break;
                case FINISHED:
                case NOT_HANDSHAKING:
                    handshakeFuture.setSuccess();
                    handshakeComplete = true;
                    break;
            }

        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (ctx != null && msg instanceof DatagramPacket packet) {
            // 128 bytes for encryption overhead
            ByteBuffer net = ByteBuffer.allocate(packet.content().readableBytes() + bus.utils.DTLS_ENCRYPTION_OVERHEAD);
            SSLEngineResult.Status status = sslEngine.wrap(packet.content().nioBuffer(), net).getStatus();
            if (status == SSLEngineResult.Status.OK) {
                net.flip();
                ctx.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(net), packet.recipient(), packet.sender()));
                promise.setSuccess();
            } else {
                promise.setFailure(new Exception(status.name()));
            }
        }
    }

}