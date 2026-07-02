package com.illiad.proxy.handler.udp;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.v5.forward.FwdAsoHandler;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
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
        try {
            Aso aso = bus.asos.getAsoByBind(ctx.channel());
            if (aso == null) {
                ReferenceCountUtil.release(packet);
                return;
            }

            InetSocketAddress sender = packet.sender();

            // 1. Unify and map the true client source address
            if (aso.getSource() == null && sender != null) {
                bus.asos.bindSource(aso, sender);
            }

            // 2. Reset the client TCP control channel idle timer
            if (aso.getAssociate() != null && aso.getAssociate().isOpen()) {
                aso.getAssociate().pipeline().fireUserEventTriggered(
                        io.netty.handler.timeout.IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT
                );
            }

            // =================================================================
            // STATE MACHINE ENFORCEMENT
            // =================================================================
            switch (this.state) {

                case CONNECTING:
                    // Handshake in-flight. Buffer subsequent packets safely behind Packet 1.
                    aso.getPacketBuffer().add(packet.retain());
                    ReferenceCountUtil.release(packet);
                    return;

                case DISCONNECTED:
                    // Advance state instantly to block subsequent packets from entering this block
                    this.state = SessionState.CONNECTING;
                    if (aso.getForward() != null) {
                        bus.asos.unbindForward(aso, aso.getForward());
                    }

                    // RECOMMENDED BEST PRACTICE: Retain and push Packet 1 straight into the queue buffer!
                    // This guarantees it is chronologically at the absolute front of the line.
                    aso.getPacketBuffer().add(packet.retain());

                    // Dynamically inject the connection handler directly after this handler instance
                    ctx.pipeline().addAfter(ctx.name(), "fwdAsoHandler", new FwdAsoHandler(bus));

                    // Hand off the packet downstream to trigger the handshake sequence.
                    // FwdAsoHandler now shares ownership and will release it.
                    ctx.fireChannelRead(bus.utils.UPSTREAM_SETUP);
                    return;

                case CONNECTED:
                    Channel forward = aso.getForward();

                    // Self-healing edge case: If the link dropped silently, trigger a reconnect
                    if (forward == null || !forward.isActive()) {
                        this.state = SessionState.CONNECTING;
                        aso.getPacketBuffer().add(packet.retain());
                        ReferenceCountUtil.release(packet);

                        ctx.pipeline().addAfter(ctx.name(), "fwdAsoHandler", new FwdAsoHandler(bus));
                        ctx.fireChannelRead(bus.utils.UPSTREAM_SETUP);
                        return;
                    }

                    // Flush stashed packets that built up during the handshake phase first (Lossless FIFO)
                    flushBufferQueue(aso);

                    // Transmit the current incoming packet immediately (if it isn't our empty trigger packet)
                    if (packet.content().readableBytes() > 0) {
                        sendOutboundPacket(forward, packet);
                    } else {
                        ReferenceCountUtil.release(packet); // Cleanly drop empty trigger packets
                    }
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
                packet.content().retain(),
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
