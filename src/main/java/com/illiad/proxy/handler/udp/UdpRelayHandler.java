package com.illiad.proxy.handler.udp;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.v5.forward.FwdAsoHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import lombok.Setter;

import java.net.InetSocketAddress;

/**
 * High-performance, thread-safe UDP proxy data router driven by a strict state enum.
 * Implements an immediate chronological FIFO queue buffer to prevent out-of-order data corruption.
 */
public class UdpRelayHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final ParamBus bus;

    // Volatile guarantees atomic visibility across different Netty EventLoop threads
    @Setter
    private volatile SessionState state = SessionState.DISCONNECTED;

    public UdpRelayHandler(ParamBus bus) {
        this.bus = bus;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        Aso aso = bus.asos.getAsoByBind(ctx.channel());
        if (aso == null) {
            ctx.fireExceptionCaught(new RuntimeException("UDPRelayHandler, Aso null... "));
            return;
        }

        // always buffer current packet first
        aso.getPacketBuffer().add(packet.content().retainedDuplicate());

        try {
            // 1. Bind client source address
            InetSocketAddress sender = packet.sender();
            if (aso.getSource() == null && sender != null) {
                bus.asos.bindSource(aso, sender);
            }

            // 2. Renew associate channel (TCP) idle timer
            Channel associate = aso.getAssociate();
            if (associate != null && associate.isOpen()) {
                associate.pipeline().fireUserEventTriggered(
                        IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT
                );
            }

            // =================================================================
            // STATE MACHINE ENFORCEMENT
            // =================================================================
            switch (this.state) {
                case CONNECTING:
                    // Handshake in-flight. New packets accumulate safely in the buffer queue.
                    return;

                case DISCONNECTED:
                    reconnect(ctx, aso);
                    return;

                case CONNECTED:
                    Channel forward = aso.getForward();
                    // Self-healing edge case: If the link dropped silently, trigger a reconnect
                    if (forward == null || !forward.isActive()) {
                        reconnect(ctx, aso);
                        return;
                    }

                    // Flush packet queue (Lossless FIFO)
                    flushQueue(aso);
                    break;
            }

        } catch (Throwable t) {
            ctx.fireExceptionCaught(t);
        }
    }

    private void reconnect(ChannelHandlerContext ctx, Aso aso) {
        this.state = SessionState.CONNECTING;
        if (aso.getForward() != null) {
            bus.asos.debindForward(aso, aso.getForward());
        }
        ctx.pipeline().addLast(new FwdAsoHandler(bus));
        ctx.fireChannelRead(bus.utils.UPSTREAM_SETUP);
    }

    public void flushQueue(Aso aso) {
        Channel forward = aso.getForward();
        if (forward == null || !forward.isActive()) {
            this.state = SessionState.DISCONNECTED;
            clearBufferQueue(aso);
            return;
        }

        // Check forward.isWritable() to prevent over-allocating memory if the network is saturated
        while (forward.isWritable() && !aso.getPacketBuffer().isEmpty()) {
            ByteBuf byteBuf = aso.getPacketBuffer().poll();
            if (!sendOutboundPacket(forward, byteBuf)) {
                // Explicitly release the packet we just polled so it doesn't leak memory!
                ReferenceCountUtil.release(byteBuf);

                // Wipe the rest of the queue since the connection is broken anyway
                clearBufferQueue(aso);
                break;
            }
        }
    }

    private boolean sendOutboundPacket(Channel forward, ByteBuf byteBuf) {
        // Scenario A: Channel died right before sending. Manually release the polled packet.
        if (forward == null || !forward.isActive()) {
            ReferenceCountUtil.release(byteBuf);
            this.state = SessionState.DISCONNECTED;
            return false;
        }

        // Construct target outbound wrapper sharing the duplicated payload buffer
        DatagramPacket fwdPacket = new DatagramPacket(
                byteBuf,
                (InetSocketAddress) forward.remoteAddress()
        );

        // Scenario B: Netty asynchronously transfers bytes to the network layer.
        // Netty automatically manages the release of fwdPacket (and its content) upon completion.
        forward.writeAndFlush(fwdPacket).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                this.state = SessionState.DISCONNECTED;
                future.channel().close();
            }
        });

        return true;
    }

    private void clearBufferQueue(Aso aso) {
        ByteBuf byteBuf;
        while ((byteBuf = aso.getPacketBuffer().poll()) != null) {
            ReferenceCountUtil.release(byteBuf);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.fireExceptionCaught(cause);
    }
}

