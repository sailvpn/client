package com.illiad.proxy.handler.udp;

import com.illiad.proxy.ParamBus;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;

public class ResHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private final ParamBus bus;

    public ResHandler(ParamBus bus) {
        this.bus = bus;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket res) throws Exception {
        if (res != null) {
            Aso aso = bus.asos.getAsobyForward(ctx.channel());
            if (aso != null) {
                Channel bind = aso.getBind();
                if (bind != null && bind.isActive()) {
                    // form a new response with the source(client) as recipient, bind local address as sender
                    DatagramPacket response = new DatagramPacket(res.content().retain(), aso.getSource(), (InetSocketAddress) bind.localAddress());
                    bind.writeAndFlush(response);
                }

            }
        }
    }
}

