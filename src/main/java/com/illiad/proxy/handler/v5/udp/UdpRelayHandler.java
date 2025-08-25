package com.illiad.proxy.handler.v5.udp;

import com.illiad.proxy.ParamBus;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

public class UdpRelayHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final ParamBus bus;

    public UdpRelayHandler(ParamBus bus) {
        this.bus = bus;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws UnknownHostException {

        // get the sender's socketaddress
        InetSocketAddress sender = packet.sender();
        Aso aso = bus.asos.getAsoByBind(ctx.channel());
        if (aso != null) {
            InetAddress asoRemoteAddr = ((InetSocketAddress) (aso.getAssociate().remoteAddress())).getAddress();
            // --- Security Check (RFC 1928) ---
            // make sure the DatagramPacket had come from the same address (IP)
            // that had initiated the UDP_ASSOCIATE on a TCP channel
            if (sender.getAddress().equals(asoRemoteAddr)) {
                // associate source
                aso.setSource(sender);

                ByteBuf buf = packet.content();
                buf.skipBytes(3); // RSV (2 bytes) + FRAG (1 byte)
                byte atyp = buf.readByte();
                InetSocketAddress destAddr = bus.utils.parseAddress(buf, atyp);
                if (destAddr != null) {

                    ByteBuf data = buf.slice(buf.readerIndex(), buf.readableBytes());
                    DatagramPacket forwardPacket = new DatagramPacket(data.retain(), destAddr);

                    Channel forward = null;
                    // acquire forward from aso's forward list by destAddr
                    for (Channel fwd : aso.getForwards()) {
                        if (destAddr.equals(fwd.remoteAddress())) {
                            forward = fwd;
                            break;
                        }
                    }
                    // already has an ephemeral socket to the final UDP server
                    if (forward != null && forward.isActive()) {
                        forward.writeAndFlush(forwardPacket)
                                .addListener((ChannelFutureListener) future1 -> {
                                    if (!future1.isSuccess()) {
                                        Channel failedForward = future1.channel();
                                        // close forward channel if write failed
                                        failedForward.close();
                                        // remove the corresponding forward
                                        Iterator<Channel> it = aso.getForwards().iterator();
                                        while (it.hasNext()) {
                                            Channel fwd = it.next();
                                            if (failedForward.id().equals(fwd.id())) {
                                                it.remove();
                                                break;
                                            }
                                        }
                                    }
                                });
                    } else {

                        // establish an ephemeral socket to the final UDP server
                        Bootstrap forwardStrap = new Bootstrap();
                        forwardStrap.group(ctx.channel().eventLoop())
                                .channel(NioDatagramChannel.class)
                                // Enable broadcasting if needed
                                .option(ChannelOption.SO_BROADCAST, true)
                                .handler(new ChannelInitializer<DatagramChannel>() {
                                    @Override
                                    protected void initChannel(DatagramChannel ch) {
                                        ch.pipeline().addLast(new ResHandler(bus));
                                    }
                                }).connect(destAddr).addListener((ChannelFutureListener) future -> {
                                    if (future.isSuccess()) {
                                        Channel fwd = future.channel();
                                        fwd.writeAndFlush(forwardPacket)
                                                .addListener((ChannelFutureListener) future1 -> {
                                                    if (future1.isSuccess()) {
                                                        // associate forward ephemeral channel to the aso
                                                        aso.getForwards().add(fwd);
                                                    } else {
                                                        fwd.close();
                                                    }
                                                });
                                    } else {
                                        future.channel().close();
                                    }
                                });
                    }
                }
            }
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
