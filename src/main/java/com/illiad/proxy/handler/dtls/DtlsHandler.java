package com.illiad.proxy.handler.dtls;

// DtlsHandler.java

import com.illiad.proxy.ParamBus;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;

/**
 * A Netty handler that manages DTLS communication over UDP using SSLEngine.
 * It handles the DTLS handshake and encrypts/decrypts data packets.
 */
public class DtlsHandler extends ChannelDuplexHandler {

    private final ParamBus bus;
    private final SSLEngine sslEngine;
    private final ByteBuffer netin;
    private final ByteBuffer appin;
    private final ByteBuffer netout;
    private final ByteBuffer appout;
    private final ByteBuffer emptyBuffer;
    private final byte[] fragment;

    private final Promise<Channel> handshakePromise = new LazyChannelPromise();
    private volatile ChannelHandlerContext context;
    private boolean handshakeComplete = false;


    public DtlsHandler(ParamBus bus, InetSocketAddress remoteAddress, ChannelPromise handshakeFuture) {
        this.bus = bus;
        this.sslEngine = bus.dtls.sslCtx.createSSLEngine(remoteAddress.getHostString(), remoteAddress.getPort());
        this.sslEngine.setUseClientMode(true);
        this.netin = ByteBuffer.allocate(bus.utils.NET_IN_SIZE);
        this.appin = ByteBuffer.allocate(bus.utils.APP_IN_SIZE);
        this.netout = ByteBuffer.allocate(bus.utils.NET_OUT_SIZE);
        this.appout = ByteBuffer.allocate(bus.utils.APP_OUT_SIZE);
        this.emptyBuffer = ByteBuffer.allocate(0);
        this.fragment = new byte[bus.utils.FRAGMENT_SIZE];
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.context = ctx;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Start DTLS handshake
        sslEngine.beginHandshake();
        sendHandshake();
    }

    private void sendHandshake() throws Exception {

        // app is empty for initial handshake
        SSLEngineResult.Status status = sslEngine.wrap(emptyBuffer, netout).getStatus();
        if (status == SSLEngineResult.Status.OK) {
            netout.flip();
            if (netout.hasRemaining()) {
                // the initial packet is ~250 bytes, should fit into a single small UDP packet
                context.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(netout), ((InetSocketAddress) context.channel().remoteAddress())))
                        .addListener(future -> {
                            //reset the netout buffer for next wrap
                            netout.clear();
                            if (!future.isSuccess()) {
                                handshakePromise.setFailure(new Exception(future.cause()));
                                context.fireExceptionCaught(new Exception(future.cause()));
                                context.close();
                            }
                        });
            }
        } else {
            // least likely that it is other status, because handshake not even started
            context.close();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (ctx != null && msg instanceof DatagramPacket packet) {
            if (handshakeComplete) {
                processData(packet);
            } else {
                doHandshake(packet);
            }
        }
    }

    private void processData(DatagramPacket packet) throws Exception {

        // append packet content and release
        netin.compact()
                .put(packet.content().nioBuffer())
                .flip();
        InetSocketAddress recipient = packet.recipient();
        InetSocketAddress sender = packet.sender();
        packet.release();
        SSLEngineResult.Status status = sslEngine.unwrap(netin, appin).getStatus();
        if (status == SSLEngineResult.Status.OK) {
            // unwarp successful, appin will be reset by subsequent handlers;
            appin.flip();
            // Process decrypted data in appIn
            context.fireChannelRead(new DatagramPacket(Unpooled.wrappedBuffer(appin), recipient, sender));
        } else if (status == SSLEngineResult.Status.CLOSED) {
            context.fireExceptionCaught(new Exception(status.name()));
            context.close();
        }
        // status == SSLEngineResult.Status.BUFFER_UNDERFLOW, do nothing
        // status == SSLEngineResult.Status.BUFFER_OVERFLOW, not possible
    }

    private void doHandshake(DatagramPacket packet) throws Exception {

        // append packet content and release
        netin.compact()
                .put(packet.content().nioBuffer())
                .flip();
        InetSocketAddress recipient = packet.recipient();
        InetSocketAddress sender = packet.sender();
        packet.release();

        SSLEngineResult.HandshakeStatus hsStatus = sslEngine.getHandshakeStatus();
        while (!handshakeComplete) {
            // use "if" rather than "switch" in order to avoid ambiguous between "break switch" and "break while"
            if (hsStatus == NEED_UNWRAP) {
                SSLEngineResult unwrap = sslEngine.unwrap(netin, appin);
                SSLEngineResult.Status uStatus = unwrap.getStatus();
                if (uStatus == SSLEngineResult.Status.OK) {
                    // unwarp successful, clear appin
                    appin.clear();
                    hsStatus = unwrap.getHandshakeStatus();
                } else if (uStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    // wait for more date
                    break;
                } else if (uStatus == SSLEngineResult.Status.CLOSED) {
                    handshakePromise.setFailure(new Exception(uStatus.name()));
                    context.fireExceptionCaught(new Exception(uStatus.name()));
                    context.close();
                    return;
                }
                // uStatus == SSLEngineResult.Status.BUFFER_OVERFLOW, not possible, appin had been set with maximum size
            } else if (hsStatus == NEED_UNWRAP_AGAIN) {
                // empty buffer applied because SSLEngine is using internally cached data from previous NEED_UNWRAP
                SSLEngineResult unwrap = sslEngine.unwrap(emptyBuffer, appin);
                SSLEngineResult.Status uStatus = unwrap.getStatus();
                if (uStatus == SSLEngineResult.Status.OK) {
                    // unwarp successful
                    appin.clear();
                    hsStatus = unwrap.getHandshakeStatus();
                } else if (uStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    // wait for more date
                    break;
                } else if (uStatus == SSLEngineResult.Status.CLOSED) {
                    handshakePromise.setFailure(new Exception(uStatus.name()));
                    context.fireExceptionCaught(new Exception(uStatus.name()));
                    context.close();
                    return;
                }
                // BUFFER_OVERFLOW is not possible, appin had been set with maximum size

            } else if (hsStatus == NEED_WRAP) {
                // empty buffer applied because SSLEngine is using internal data
                SSLEngineResult wrap = sslEngine.wrap(emptyBuffer, netout);
                SSLEngineResult.Status wStatus = wrap.getStatus();
                if (wStatus == SSLEngineResult.Status.OK) {
                    netout.flip();
                    doSendBlocking(recipient, sender);
                    hsStatus = wrap.getHandshakeStatus();
                } else if (wStatus == SSLEngineResult.Status.CLOSED) {
                    handshakePromise.setFailure(new Exception(wStatus.name()));
                    context.fireExceptionCaught(new Exception(wStatus.name()));
                    context.close();
                    return;
                }
                // BUFFER_UNDERFLOW is not possible, SSLEngine is using internal data
                // BUFFER_OVERFLOW is not possible, netout had been set with maximum size

            } else if (hsStatus == NEED_TASK) {
                Runnable task;
                while ((task = sslEngine.getDelegatedTask()) != null) {
                    task.run();
                }
                hsStatus = sslEngine.getHandshakeStatus();

            } else if (hsStatus == FINISHED || hsStatus == NOT_HANDSHAKING) {

                // clear all buffers for data transmission
                netin.clear();
                netout.clear();
                appin.clear();
                appout.clear();
                handshakeComplete = true;
                handshakePromise.setSuccess(context.channel());
            }

        }
    }


    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (handshakeComplete && sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING && msg instanceof DatagramPacket packet) {
            SSLEngineResult.Status status = sslEngine.wrap(packet.content().nioBuffer(), netout).getStatus();
            if (status == SSLEngineResult.Status.OK) {
                InetSocketAddress recpient = packet.recipient();
                InetSocketAddress sender = packet.sender();
                packet.content().release();
                netout.flip();
                doSendBlocking(recpient, sender);
                promise.setSuccess();
            } else {
                promise.setFailure(new Exception(status.name() + System.lineSeparator() + sslEngine.getHandshakeStatus()));
            }

        }
    }

    /**
     * send netout blockingly, netout MUST be flipped before calling this function
     * @param recipient
     * @param sender
     */
    private void doSendBlocking(InetSocketAddress recipient, InetSocketAddress sender) {

        if (netout.hasRemaining()) {
            ChannelFuture future;
            if (netout.remaining() < bus.utils.FRAGMENT_SIZE) {
                future = context.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(netout), recipient, sender));
            } else {
                netout.get(fragment);
                future = context.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(fragment), recipient, sender));
            }

            if (future != null) {
                try {
                    // Block until the operation completes
                    future.sync();
                    // Recursively call for remaining fragments if needed
                    if (netout.hasRemaining()) {
                        netout.compact().flip();
                        doSendBlocking(recipient, sender);
                    }
                } catch (InterruptedException e) {
                    // The future failed, handle the exception
                    context.fireExceptionCaught(e);
                    context.close();
                }
            }
        }
    }


    public Future<Channel> handshakeFuture() {
        return handshakePromise;
    }

    private final class LazyChannelPromise extends DefaultPromise<Channel> {

        @Override
        protected EventExecutor executor() {
            if (context == null) {
                throw new IllegalStateException();
            }
            return context.executor();
        }

        @Override
        protected void checkDeadLock() {
            if (context == null) {
                // If ctx is null the handlerAdded(...) callback was not called, in this case the checkDeadLock()
                // method was called from another Thread then the one that is used by ctx.executor(). We need to
                // guard against this as a user can see a race if handshakeFuture().sync() is called but the
                // handlerAdded(..) method was not yet as it is called from the EventExecutor of the
                // ChannelHandlerContext. If we not guard against this super.checkDeadLock() would cause an
                // IllegalStateException when trying to call executor().
                return;
            }
            super.checkDeadLock();
        }
    }

}