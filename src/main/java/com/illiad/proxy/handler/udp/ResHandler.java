package com.illiad.proxy.handler.udp;

import com.illiad.proxy.ParamBus;

import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

/**
 * Handles processing and high-speed routing of return UDP packets from the internet back to the client.
 */
public class ResHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private final ParamBus bus;

    public ResHandler(ParamBus bus) {
        // FIX: Keep autoRelease as false because we are manually handing off packet memory
        super(false);
        this.bus = bus;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket res) throws Exception {
        try {
            if (res == null) return;

            // Resolve the overarching session tracker via the O(1) multi-index lookup map
            Aso aso = bus.asos.getAsobyForward(ctx.channel());
            if (aso == null) {
                ReferenceCountUtil.release(res);
                return;
            }

            Channel bind = aso.getBind();
            if (bind != null && bind.isActive()) {

                // 1. FIX: Reset the Client TCP control connection idle timer on every incoming return packet.
                // This ensures downloading heavy streams doesn't trigger an accidental idle timeout eviction.
                if (aso.getAssociate() != null && aso.getAssociate().isOpen()) {
                    aso.getAssociate().pipeline().fireUserEventTriggered(
                            io.netty.handler.timeout.IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT
                    );
                }

                // 2. FIX: Omit the local sender interface parameter to let the OS handle routing naturally.
                // Pass the retained buffer context and target the stashed user source endpoint directly.
                DatagramPacket response = new DatagramPacket(
                        res.content().retain(),
                        aso.getSource()
                );

                bind.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        // If writing back to the client machine fails completely, trigger a teardown
                        bus.asos.removeAsoByBind(bind);
                    }
                });
            }

            // Cleanly free the incoming wrapper container from off-heap system memory
            ReferenceCountUtil.release(res);

        } catch (Throwable t) {
            ReferenceCountUtil.release(res);
            ctx.fireExceptionCaught(t);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // Find the context via the active forward socket reference and clear indices atomically
        Aso aso = bus.asos.getAsobyForward(ctx.channel());
        if (aso != null) {
            bus.asos.removeAsoByBind(aso.getBind());
        } else {
            ctx.close();
        }
    }
}


