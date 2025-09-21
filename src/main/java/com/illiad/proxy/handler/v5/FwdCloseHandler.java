package com.illiad.proxy.handler.v5;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.udp.Aso;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
/**
 * this handler is attached to the forward associate (TCP) channel, it closes the corresponding
 * forward UDP channel
 */
public class FwdCloseHandler extends ChannelDuplexHandler {
    private final ParamBus bus;

    public FwdCloseHandler(ParamBus bus) {
        this.bus = bus;
    }

    /**
     * when debugging/tracing, comment out channelActive(), and exceptionCaught()
     * otherwise the tcp channel will be closed(thus the UDP relay channel) long before debugging finished
     *
     */

    /**
     * IMPORTANT: this event will close forward UDP channel before any (UDP) message was forwarded
     *
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {

        // close the UDP forward channel when the forward associate tcp channel was closed
        Aso aso = bus.asos.removeAsobyFwdAssociate(ctx.channel());
        if (aso != null) {
            if (!aso.getForwards().isEmpty() && aso.getForwards().get(0) != null && aso.getForwards().get(0).isActive()) {
                aso.getForwards().get(0).close();
                aso.getForwards().remove(0);
            }
        }
        ctx.close();
    }
    **/

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // close the UDP forward channel when the forward associate tcp channel was closed
        Aso aso = bus.asos.removeAsobyFwdAssociate(ctx.channel());
        if (aso != null) {
            if (!aso.getForwards().isEmpty() && aso.getForwards().get(0) != null && aso.getForwards().get(0).isActive()) {
                aso.getForwards().get(0).close();
                aso.getForwards().remove(0);
            }
        }
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }

}
