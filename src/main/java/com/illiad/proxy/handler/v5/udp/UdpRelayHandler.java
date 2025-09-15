package com.illiad.proxy.handler.v5.udp;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.v5.FwdAsoHandler;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class UdpRelayHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final ParamBus bus;

    public UdpRelayHandler(ParamBus bus) {
        super(false);
        this.bus = bus;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {

        Aso aso = bus.asos.getAsoByBind(ctx.channel());
        if (packet != null && aso != null) {
            InetAddress asoRemoteAddr = ((InetSocketAddress) (aso.getAssociate().remoteAddress())).getAddress();
            InetSocketAddress sender = packet.sender();
            // --- Security Check (RFC 1928) ---
            // make sure the DatagramPacket had come from the same address (IP)
            // that had initiated the UDP_ASSOCIATE on a TCP channel
            if (sender != null && sender.getAddress().equals(asoRemoteAddr)) {
                // store the source address
                if (aso.getSource() == null) {
                    aso.setSource(sender);
                }
                // the 1st leg's forward is the 2nd legs' bind, and should be only 1 channel if exists
                if (aso.getForwards().isEmpty() || aso.getForwards().get(0) == null || !aso.getForwards().get(0).isActive()) {
                    // no valid forward, proceed to initiate UDP_ASSOCIATE towards 2nd leg
                    ctx.pipeline().addLast(new FwdAsoHandler(bus));
                    ctx.fireChannelRead(packet);
                } else {
                    // get forward(2nd leg's bind), and relay the DataGramPacket
                    Channel forward = aso.getForwards().get(0);
                    // form a new DataGramPacket, with the orignal SOCKS5 UDP packet(from client) as content, 2nd leg's bind as recepient, 1st leg's bind as sender
                    DatagramPacket fwdPacket = new DatagramPacket(packet.content(), (InetSocketAddress) forward.remoteAddress(), (InetSocketAddress) ctx.channel().localAddress());
                    forward.writeAndFlush(fwdPacket)
                            .addListener((ChannelFutureListener) future1 -> {
                                        ReferenceCountUtil.release(packet);
                                        if (!future1.isSuccess()) {
                                            Channel failedForward = future1.channel();
                                            // close forward channel if write failed
                                            failedForward.close();
                                            // remove the corresponding forward
                                            aso.getForwards().remove(0);
                                        }
                                    }
                            );
                }
            } else {
                ReferenceCountUtil.release(packet);
            }
        } else {
            ReferenceCountUtil.release(packet);
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // remove the associate-bind-source association
        Aso aso = bus.asos.removeAsoByBind(ctx.channel());
        if (aso != null) {
            Channel associate = aso.getAssociate();
            if (associate != null && associate.isActive()) {
                associate.close();
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
