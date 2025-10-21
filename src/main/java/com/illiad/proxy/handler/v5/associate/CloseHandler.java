package com.illiad.proxy.handler.v5.associate;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.udp.Aso;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * this handler is attached to the associate (TCP) channel, it closes the corresponding
 * UDP bind channel and all those forward channels when the associate channel is closed or exception
 */
public class CloseHandler extends ChannelDuplexHandler {
    private final ParamBus bus;

    public CloseHandler(ParamBus bus) {
        this.bus = bus;
    }

    /**
     * when debugging/tracing, comment out channelActive(), and exceptionCaught()
     * otherwise the tcp channel will be closed(thus the UDP relay channel) long before debugging finished
     *
     */

    /**
     * IMPORTANT: this event will close the UDP relay channel before any (UDP)message was relayed
     *
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {

        // close the UDP relay channel when the associate tcp channel was closed
        Aso aso = bus.asos.removeAsobyAssociate(ctx.channel());
        if (aso != null) {
            Channel bind = aso.getBind();
            if (bind != null && bind.isActive()) {
                bind.close();
            }
            // close all forwards associated
            for (Channel fwd : aso.getForwards()) {
                if (fwd != null && fwd.isActive()) {
                    fwd.close();
                }
            }
        }
        ctx.close();
    }

     **/

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // close the UDP relay channel when the associate tcp channel was closed
        Aso aso = bus.asos.removeAsobyAssociate(ctx.channel());
        if (aso != null) {
            Channel bind = aso.getBind();
            if (bind != null && bind.isActive()) {
                bind.close();
            }
            // close all forwards associated
            for (Channel fwd : aso.getForwards()) {
                if (fwd != null && fwd.isActive()) {
                    fwd.close();
                }
            }
        }
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }

}
