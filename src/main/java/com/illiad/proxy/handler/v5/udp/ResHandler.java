package com.illiad.proxy.handler.v5.udp;

import com.illiad.proxy.ParamBus;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
                    ByteBuf content = res.content();
                    InetSocketAddress bindSocketAddr = res.sender();
                    ByteBuf header = Unpooled.buffer();
                    // create  SOCKS5 UDP header
                    // SOCKS5 Header contains the bind address of UDP Server
                    bus.utils.createSocks5UdpHeader(header, bindSocketAddr);
                    // Combine the SOCKS5 header with the original response content
                    ByteBuf socksPacket = Unpooled.wrappedBuffer(header, content.retain());
                    // Create a new DatagramPacket to send back to the client
                    // The recipient is the SOCKS5 client's UDP address.
                    DatagramPacket response = new DatagramPacket(socksPacket, aso.getSource());
                    bind.writeAndFlush(response);
                }

            }
        }
    }
}

