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

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;

/**
 * A Netty handler that manages DTLS communication over UDP using SSLEngine.
 * It handles the DTLS handshake and encrypts/decrypts data packets.
 */
public class DtlsHandler extends ChannelDuplexHandler {

    private final ParamBus bus;
    private final SSLEngine sslEngine;
    // if limit < capacity buffer is in read mode; if limit == capacity, buffer is in write mode
    private final ByteBuffer netin;
    private final ByteBuffer appin;
    private final ByteBuffer netout;
    private final ByteBuffer emptyBuffer;
    private final byte[] fragment;

    private final Promise<Channel> handshakePromise = new LazyChannelPromise();
    private volatile ChannelHandlerContext context;

    public DtlsHandler(ParamBus bus, InetSocketAddress remoteAddress) {
        this.bus = bus;
        this.sslEngine = bus.dtls.sslCtx.createSSLEngine(remoteAddress.getHostString(), remoteAddress.getPort());
        this.sslEngine.setUseClientMode(true);
        this.netin = ByteBuffer.allocate(bus.utils.NET_IN_SIZE);
        this.appin = ByteBuffer.allocate(bus.utils.APP_IN_SIZE);
        this.netout = ByteBuffer.allocate(bus.utils.NET_OUT_SIZE);
        this.emptyBuffer = ByteBuffer.allocate(0);
        this.fragment = new byte[bus.utils.FRAGMENT_SIZE];
    }

    /**
     * DtlsHandler is added AFTER channel was active, (see FwdAsoAckHandler),
     * channelActive() is never triggered
     * @param ctx ChannelHandlerContext
     */
    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.context = ctx;

        try {
            // Start DTLS handshake
            sslEngine.beginHandshake();
            sendHandshake();
        } catch (SSLException e) {
            context.fireExceptionCaught(e);
        }

    }

    private void sendHandshake() {

        try {
            // empty buffer for initial handshake
            SSLEngineResult.Status status = sslEngine.wrap(emptyBuffer, netout).getStatus();
            if (status == SSLEngineResult.Status.OK) {
                doSend((InetSocketAddress) context.channel().remoteAddress(), (InetSocketAddress) context.channel().localAddress());
            }
            // least likely for other status, because handshake not even started

        } catch (SSLException e) {
            context.fireExceptionCaught(e);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        if (msg instanceof DatagramPacket packet) {

            //append network data to netin, and release packet
            append(packet);
            InetSocketAddress recipient = packet.recipient();
            InetSocketAddress sender = packet.sender();
            packet.release();

            if (sslEngine.getHandshakeStatus() == NOT_HANDSHAKING) {
                decrypt(recipient, sender);
            } else {
                doHandshake(recipient, sender);
            }
        }
    }

    private void append(DatagramPacket packet) {
        // compact netin if it is in read mode
        if (netin.limit() < netin.capacity()) {
            netin.compact();
        }
        netin.put(packet.content().nioBuffer());
    }

    private void decrypt(InetSocketAddress recipient, InetSocketAddress sender) {

        // flip netin if it's in write mode
        if (netin.limit() == netin.capacity()) {
            netin.flip();
        }

        try {
            SSLEngineResult.Status status = sslEngine.unwrap(netin, appin).getStatus();
            if (status == SSLEngineResult.Status.OK) {
                // unwarp successful, flip and forward appin;
                appin.flip();
                context.fireChannelRead(new DatagramPacket(Unpooled.wrappedBuffer(appin), recipient, sender));
            } else if (status == SSLEngineResult.Status.CLOSED) {
                context.fireExceptionCaught(new Exception(status.name()));
            }
            // status == SSLEngineResult.Status.BUFFER_UNDERFLOW, not possible
            // status == SSLEngineResult.Status.BUFFER_OVERFLOW, not possible
        } catch (SSLException e) {
            context.fireExceptionCaught(e);
        }
    }

    /**
     *
     * FINISHED is a transient state generated only by callings to wrap()/unwrap().
     * SSLEngine.getHandshakeStatus() never returns FINISHED
     * The handshake loop should be checked by NOT_HANDSHAKING, which indicates that the handshake has truly completed.
     * checking for FINISHED will always fail.
     *
     * @param recipient InetSocketAddress
     * @param sender    InetSocketAddress
     */
    private void doHandshake(InetSocketAddress recipient, InetSocketAddress sender) {

        try {
            SSLEngineResult.HandshakeStatus hsStatus = sslEngine.getHandshakeStatus();
            while (hsStatus != NOT_HANDSHAKING) {
                // use "if" rather than "switch" in order to avoid ambiguous between "break switch" and "break while"
                if (hsStatus == NEED_UNWRAP) {
                    // flip netin if it's in write mode
                    if (netin.limit() == netin.capacity()) {
                        netin.flip();
                    }
                    SSLEngineResult.Status uStatus = sslEngine.unwrap(netin, appin).getStatus();
                    if (uStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        // wait for more data from net peer
                        break;
                    } else if (uStatus == SSLEngineResult.Status.CLOSED) {
                        handshakePromise.setFailure(new Exception(uStatus.name()));
                        context.fireExceptionCaught(new Exception(uStatus.name()));
                        return;
                    }
                    // uStatus == SSLEngineResult.Status.OK, do nothing
                    // uStatus == SSLEngineResult.Status.BUFFER_OVERFLOW, not possible, appin had been set with maximum size
                } else if (hsStatus == NEED_UNWRAP_AGAIN) {
                    // empty buffer applied because SSLEngine is using internally cached data from previous NEED_UNWRAP
                    SSLEngineResult.Status uStatus = sslEngine.unwrap(emptyBuffer, appin).getStatus();
                    if (uStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        // flip netin if it's in write mode
                        if (netin.limit() == netin.capacity()) {
                            netin.flip();
                        }
                        // netin empty, break to wait for more data frm net peer
                        if (!netin.hasRemaining()) {
                            break;
                        }
                        // data available in netin, proceed wtih local handshake
                    } else if (uStatus == SSLEngineResult.Status.CLOSED) {
                        handshakePromise.setFailure(new Exception(uStatus.name()));
                        context.fireExceptionCaught(new Exception(uStatus.name()));
                        return;
                    }
                    // uStatus == SSLEngineResult.Status.OK, do nothing
                    // BUFFER_OVERFLOW is not possible, appin had been set with maximum size

                } else if (hsStatus == NEED_WRAP) {
                    // empty buffer applied because SSLEngine is using internal data
                    SSLEngineResult.Status wStatus = sslEngine.wrap(emptyBuffer, netout).getStatus();
                    if (wStatus == SSLEngineResult.Status.OK) {
                        doSend(recipient, sender);
                    } else if (wStatus == SSLEngineResult.Status.CLOSED) {
                        handshakePromise.setFailure(new Exception(wStatus.name()));
                        context.fireExceptionCaught(new Exception(wStatus.name()));
                        return;
                    }
                    // BUFFER_UNDERFLOW is not possible, SSLEngine is using internal data
                    // BUFFER_OVERFLOW is not possible, netout had been set with maximum size

                } else if (hsStatus == NEED_TASK) {
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        task.run();
                    }

                }

                // update status and repeat
                hsStatus = sslEngine.getHandshakeStatus();

            }

            // "while" statement could be broke in the scenario of "BUFFER_UNDERFLOW"
            if (hsStatus == NOT_HANDSHAKING) {
                // handshake finished, clear appin, buffers for data transmission
                netin.clear();
                appin.clear();
                handshakePromise.setSuccess(context.channel());
            }

        } catch (SSLException e) {
            handshakePromise.setFailure(e);
            context.fireExceptionCaught(e);
        }
    }

    /**
     * encrpt message and send
     *
     * @param ctx ChannelHandlerContext
     * @param msg DatagramPacket
     * @param promise ChannelPromise
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        try {
            if (sslEngine.getHandshakeStatus() == NOT_HANDSHAKING && msg instanceof DatagramPacket packet) {
                SSLEngineResult.Status status = sslEngine.wrap(packet.content().nioBuffer(), netout).getStatus();
                if (status == SSLEngineResult.Status.OK) {
                    InetSocketAddress recpient = packet.recipient();
                    InetSocketAddress sender = packet.sender();
                    packet.content().release();
                    doSend(recpient, sender);
                    promise.setSuccess();
                } else {
                    promise.setFailure(new Exception(status.name() + System.lineSeparator() + sslEngine.getHandshakeStatus()));
                }

            }
        } catch (SSLException e) {
            promise.setFailure(e);
            context.fireExceptionCaught(e);
        }
    }

    /**
     * send netout blockingly
     *
     * @param recipient, the remote peer address
     * @param sender,    the local address
     */
    private void doSend(InetSocketAddress recipient, InetSocketAddress sender) {
        // flip netout if it's in write mode
        if(netout.limit() == netout.capacity()) {
            netout.flip();
        }
        try {
            int length;
            // repeat until netout empty
            while (netout.hasRemaining()) {
                length = Math.min(netout.remaining(), bus.utils.FRAGMENT_SIZE);
                // copy bytes to fragment
                netout.get(fragment, 0, length);
                // Block until the operation completes
                context.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(fragment, 0, length), recipient, sender)).sync();
            }
            netout.clear();
        } catch (InterruptedException e) {
            // The future failed, handle the exception
            context.fireExceptionCaught(e);
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