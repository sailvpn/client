package com.illiad.proxy.handler.dtls;

import com.illiad.proxy.ParamBus;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;

/**
 * A Netty handler that manages DTLS communication over UDP using SSLEngine.
 * It handles the DTLS handshake and encrypts/decrypts data packets.
 */
public class DtlsHandler extends ChannelDuplexHandler {

    private static final Logger logger = LoggerFactory.getLogger(DtlsHandler.class);
    // Define a separate thread pool for blocking SSL tasks
    private static final ExecutorService sslTaskExecutor = Executors.newCachedThreadPool();

    private final ParamBus bus;
    private SSLEngine sslEngine;
    private final InetSocketAddress remoteAddress;
    // Buffer mode tracking: true = write mode, false = read mode
    private final ByteBuffer netin;
    private final ByteBuffer appin;
    private final ByteBuffer netout;
    private final ByteBuffer emptyBuffer;

    private boolean netinWriteMode = true;
    private boolean netoutWriteMode = true;

    private final Promise<Channel> handshakePromise = new LazyChannelPromise();
    private volatile ChannelHandlerContext context;
    private final Object inboundLock = new Object();
    private final Object outboundLock = new Object();

    public DtlsHandler(ParamBus bus, InetSocketAddress remoteAddress) {
        this.bus = bus;
        this.remoteAddress = remoteAddress;
        this.netin = ByteBuffer.allocate(bus.utils.NET_IN_SIZE);
        this.appin = ByteBuffer.allocate(bus.utils.APP_IN_SIZE);
        this.netout = ByteBuffer.allocate(bus.utils.NET_OUT_SIZE);
        this.emptyBuffer = ByteBuffer.allocate(0);
    }

    /**
     * DtlsHandler is added AFTER channel was active, (see FwdAsoAckHandler),
     * channelActive() is never triggered
     *
     * @param ctx ChannelHandlerContext
     */
    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.context = ctx;
        // 1. Get the Netty SslContext (configured by OpenSSL provider and DTLS)
        // 2. Create the SSLEngine (Netty's engine implements javax.net.ssl.SSLEngine). This engine will use native OpenSSL under the hood!
        sslEngine = bus.cert.dtlsCtx.newEngine(ctx.alloc(), remoteAddress.getHostString(), remoteAddress.getPort());
        try {
            // Start DTLS handshake
            sslEngine.beginHandshake();
            sendHandshake();
        } catch (SSLException e) {
            context.fireExceptionCaught(e);
        }

        /*
        // Add handshake timeout
        ctx.executor().schedule(() -> {
            if (!handshakePromise.isDone()) {
                SSLException timeout = new SSLHandshakeException("DTLS handshake timeout");
                handshakePromise.setFailure(timeout);
                logger.error("DTLS handshake timeout for channel {}", ctx.channel());
                ctx.close();
            }
        }, 30, TimeUnit.SECONDS);

         */

    }

    private void sendHandshake() {

        synchronized (outboundLock) {
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
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof DatagramPacket packet) {
            append(packet);

            if (sslEngine.getHandshakeStatus() == NOT_HANDSHAKING) {
                decrypt(packet.recipient(), packet.sender());
            } else {
                // Handshake session loops back at SSLEngine, switch sender, recipient
                doHandshake(packet.sender(), packet.recipient());
            }
        }
    }

    /**
     * Append network data to netin and release packet content
     */
    private void append(DatagramPacket packet) {
        synchronized (inboundLock) {
            // Switch to write mode if needed
            if (!netinWriteMode) {
                netin.compact();
                netinWriteMode = true;
            }

            netin.put(packet.content().nioBuffer());
            packet.content().release();
        }
    }

    /**
     * Decrypt application data and forward to next handler
     */
    private void decrypt(InetSocketAddress recipient, InetSocketAddress sender) {
        synchronized (inboundLock) {
            // Switch to read mode if needed
            if (netinWriteMode) {
                netin.flip();
                netinWriteMode = false;
            }

            try {
                SSLEngineResult result = sslEngine.unwrap(netin, appin);
                appin.flip();

                if (result.getHandshakeStatus() == NOT_HANDSHAKING) {
                    SSLEngineResult.Status status = result.getStatus();

                    if (status == SSLEngineResult.Status.OK && appin.hasRemaining()) {
                        // ✅ Fixed: Allocate new buffer instead of reusing shared array
                        ByteBuf buf = context.alloc().buffer(appin.remaining());
                        buf.writeBytes(appin);
                        context.fireChannelRead(new DatagramPacket(buf, recipient, sender));

                    } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        throw new SSLException("Application buffer overflow - increase APP_IN_SIZE");

                    } else if (status == SSLEngineResult.Status.CLOSED) {
                        logger.warn("SSL engine closed during decrypt");
                        context.close();
                    }
                    // status == SSLEngineResult.Status.BUFFER_UNDERFLOW, not possible
                }

                appin.clear();

            } catch (SSLException e) {
                logger.error("Decryption error", e);
                context.fireExceptionCaught(e);
                context.close();
            }
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
                if (hsStatus == NEED_UNWRAP) {
                    synchronized (inboundLock) {
                        if (netinWriteMode) {
                            netin.flip();
                            netinWriteMode = false;
                        }
                        SSLEngineResult.Status uStatus = sslEngine.unwrap(netin, appin).getStatus();
                        if (uStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                            break; // Wait for more data
                        } else if (uStatus == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                            throw new SSLException("Handshake buffer overflow - increase APP_IN_SIZE");
                        } else if (uStatus == SSLEngineResult.Status.CLOSED) {
                            throw new SSLException("SSL engine closed during handshake");
                        }
                    }

                } else if (hsStatus == NEED_UNWRAP_AGAIN) {
                    synchronized (inboundLock) {
                        // empty buffer applied because SSLEngine is using internally cached data from previous NEED_UNWRAP
                        SSLEngineResult.Status uStatus = sslEngine.unwrap(emptyBuffer, appin).getStatus();
                        if (uStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                            if (netinWriteMode) {
                                netin.flip();
                                netinWriteMode = false;
                            }
                            if (!netin.hasRemaining()) {
                                break; // Wait for more data
                            }
                        } else if (uStatus == SSLEngineResult.Status.CLOSED) {
                            throw new SSLException("SSL engine closed during handshake");
                        }
                    }

                } else if (hsStatus == NEED_WRAP) {
                    synchronized (outboundLock) {
                        // empty buffer applied because SSLEngine is using internal data
                        SSLEngineResult.Status wStatus = sslEngine.wrap(emptyBuffer, netout).getStatus();
                        if (wStatus == SSLEngineResult.Status.OK) {
                            doSend(recipient, sender);
                        } else if (wStatus == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                            throw new SSLException("Handshake output buffer overflow - increase NET_OUT_SIZE");
                        } else if (wStatus == SSLEngineResult.Status.CLOSED) {
                            throw new SSLException("SSL engine closed during handshake");
                        }
                        // BUFFER_UNDERFLOW is not possible, SSLEngine is using internal data
                    }

                } else if (hsStatus == NEED_TASK) {
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        final Runnable currentTask = task;
                        sslTaskExecutor.execute(() -> {
                            try {
                                currentTask.run(); // Run the blocking task in a background thread
                            } finally {
                                // Return to the EventLoop to resume the handshake
                                context.executor().execute(() -> doHandshake(recipient, sender));
                            }
                        });
                    }
                    return; // Exit the current loop; it will resume once tasks finish
                }

                hsStatus = sslEngine.getHandshakeStatus();
            }

            // Handshake completed successfully
            if (hsStatus == NOT_HANDSHAKING) {
                synchronized (inboundLock) {
                    netin.clear();
                    appin.clear();
                    netinWriteMode = true;
                }

                logger.info("DTLS handshake completed for channel {}", context.channel());
                handshakePromise.setSuccess(context.channel());
            }

        } catch (SSLException e) {
            logger.error("DTLS handshake failed", e);
            handshakePromise.setFailure(e);
            context.fireExceptionCaught(e);
            context.close();
        }
    }

    /**
     * Encrypt outgoing message
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof DatagramPacket packet)) {
            ctx.write(msg, promise);
            return;
        }

        synchronized (outboundLock) {
            try {
                if (sslEngine.getHandshakeStatus() != NOT_HANDSHAKING) {
                    promise.setFailure(new IllegalStateException("Handshake not completed"));
                    packet.content().release();
                    return;
                }

                SSLEngineResult.Status status = sslEngine.wrap(packet.content().nioBuffer(), netout).getStatus();
                packet.content().release();
                if (status == SSLEngineResult.Status.OK) {
                    doSend(packet.recipient(), packet.sender());
                    promise.setSuccess();

                } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    promise.setFailure(new SSLException("Output buffer overflow - increase NET_OUT_SIZE"));

                } else {
                    promise.setFailure(new SSLException("Encryption failed: " + status));
                }

            } catch (SSLException e) {
                promise.setFailure(e);
                context.fireExceptionCaught(e);
            }
        }
    }

    /**
     * ✅ FIXED: Non-blocking send with proper buffer copying
     * Fragments large packets and sends asynchronously
     */
    private void doSend(InetSocketAddress recipient, InetSocketAddress sender) {
        if (netoutWriteMode) {
            netout.flip();
            netoutWriteMode = false;
        }

        while (netout.hasRemaining()) {
            int fragmentSize = Math.min(netout.remaining(), bus.utils.FRAGMENT_SIZE);
            // ✅ Use ByteBuffer slice for zero-copy view
            ByteBuffer slice = netout.slice();
            slice.limit(fragmentSize);
            ByteBuf buf = context.alloc().buffer(fragmentSize);
            buf.writeBytes(slice); // Reads entire slice
            netout.position(netout.position() + fragmentSize); // Advance position
            context.write(new DatagramPacket(buf, recipient, sender));
        }
        context.flush();
        netout.clear();
        netoutWriteMode = true;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        try {
            sslEngine.closeOutbound();
        } catch (Exception e) {
            logger.warn("Error closing SSL engine", e);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception in DTLS handler", cause);
        ctx.close();
    }

    public Future<Channel> handshakeFuture() {
        return handshakePromise;
    }

    /**
     * Lazy promise that delays executor resolution until handler is added
     */
    private final class LazyChannelPromise extends DefaultPromise<Channel> {

        @Override
        protected EventExecutor executor() {
            if (context == null) {
                throw new IllegalStateException("Handler not added to pipeline");
            }
            return context.executor();
        }

        @Override
        protected void checkDeadLock() {
            if (context == null) {
                // Handler not yet added - skip deadlock check
                return;
            }
            super.checkDeadLock();
        }
    }

}