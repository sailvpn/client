package com.illiad.proxy.handler.udp;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.v5.forward.FwdAsoHandler;
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
    @Setter
    private SessionState state = SessionState.DISCONNECTED;

    public UdpRelayHandler(ParamBus bus) {
        super(false); // Manual lifecycle management is mandatory when handling buffer queues
        this.bus = bus;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {

        Aso aso = bus.asos.getAsoByBind(ctx.channel());
        if (aso == null) {
            ReferenceCountUtil.release(packet);
            return;
        }
        // always buffer current packet first
        aso.getPacketBuffer().add(packet.retain());

        try {
            // 1. bind client source address
            InetSocketAddress sender = packet.sender();
            if (aso.getSource() == null && sender != null) {
                bus.asos.bindSource(aso, sender);
            }

            // 2. Renew associate channel(TCP) idle timer
            if (aso.getAssociate() != null && aso.getAssociate().isOpen()) {
                aso.getAssociate().pipeline().fireUserEventTriggered(
                        IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT
                );
            }

            // =================================================================
            // STATE MACHINE ENFORCEMENT
            // =================================================================
            switch (this.state) {

                case CONNECTING:
                    // Handshake in-flight.
                    return;
                case DISCONNECTED:
                    // Advance state instantly to block subsequent packets from reaching here
                    this.state = SessionState.CONNECTING;
                    // disband a previous forward
                    if (aso.getForward() != null) {
                        bus.asos.debindForward(aso, aso.getForward());
                    }

                    ctx.pipeline().addLast(new FwdAsoHandler(bus));
                    ctx.fireChannelRead(bus.utils.UPSTREAM_SETUP);
                    return;

                case CONNECTED:
                    Channel forward = aso.getForward();
                    // Self-healing edge case: If the link dropped silently, trigger a reconnect
                    if (forward == null || !forward.isActive()) {
                        this.state = SessionState.CONNECTING;
                        bus.asos.debindForward(aso, forward);
                        ctx.pipeline().addLast(new FwdAsoHandler(bus));
                        ctx.fireChannelRead(bus.utils.UPSTREAM_SETUP);
                        return;
                    }

                    // Flush packets queue(Lossless FIFO), there at least one packet waiting to be transmitted
                    flushBufferQueue(aso);
            }

        } catch (Throwable t) {
            ReferenceCountUtil.release(packet);
            ctx.fireExceptionCaught(t);
        }
    }

    private void flushBufferQueue(Aso aso) {
        Channel forward = aso.getForward();
        if (forward == null || !forward.isActive()) {
            this.state = SessionState.DISCONNECTED;
            return;
        }

        DatagramPacket bufferedPacket;
        // Condition guard: Stop flushing immediately if an active write indicates the channel died midway
        while ((bufferedPacket = aso.getPacketBuffer().poll()) != null) {
            if (!sendOutboundPacket(forward, bufferedPacket)) {
                break;
            }
        }
    }

    private boolean sendOutboundPacket(Channel forward, DatagramPacket packet) {
        if (forward == null || !forward.isActive()) {
            ReferenceCountUtil.release(packet);
            this.state = SessionState.DISCONNECTED;
            return false;
        }

        DatagramPacket fwdPacket = new DatagramPacket(
                packet.content(),
                (InetSocketAddress) forward.remoteAddress()
        );

        forward.writeAndFlush(fwdPacket).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                future.channel().close();
                this.state = SessionState.DISCONNECTED;
            }
        });

        ReferenceCountUtil.release(packet);
        return true;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        bus.asos.removeAsoByBind(ctx.channel());
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }
}
