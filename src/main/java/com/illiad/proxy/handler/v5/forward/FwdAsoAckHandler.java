package com.illiad.proxy.handler.v5.forward;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.dtls.DtlsHandler;
import com.illiad.proxy.handler.udp.Aso;
import com.illiad.proxy.handler.udp.ResHandler;
import com.illiad.proxy.handler.udp.UdpRelayHandler;
import com.illiad.proxy.handler.udp.SessionState;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import java.net.InetSocketAddress;

/**
 * Handles the SOCKS5 UDP_ASSOCIATE acknowledgment and spins up the UDP forwarding leg.
 */
public class FwdAsoAckHandler extends SimpleChannelInboundHandler<Socks5CommandResponse> {

    private final ParamBus bus;

    public FwdAsoAckHandler(ParamBus bus) {
        this.bus = bus;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5CommandResponse res) {
        if (res != null && res.status() == Socks5CommandStatus.SUCCESS) {

            Aso aso = bus.asos.getAsobyFwdAssociate(ctx.channel());
            if (aso != null) {

                ctx.channel().closeFuture().addListener((ChannelFutureListener) closeFuture -> bus.asos.removeAsobyFwdAssociate(ctx.channel()));

                final ChannelPipeline pipeline = ctx.pipeline();
                String prefix = bus.namer.getPrefix();
                for (String name : pipeline.names()) {
                    if (name.startsWith(prefix)) {
                        pipeline.remove(name);
                    }
                }

                Bootstrap fwdBootStrap = new Bootstrap();
                fwdBootStrap.group(ctx.channel().eventLoop())
                        .channel(NioDatagramChannel.class)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                        .handler(new ChannelInitializer<DatagramChannel>() {
                            @Override
                            protected void initChannel(DatagramChannel ch) {
                            }
                        });

                fwdBootStrap.connect(res.bndAddr(), res.bndPort()).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        Channel fwdUdpChannel = future.channel();
                        DtlsHandler dtlsHandler = new DtlsHandler(bus);

                        fwdUdpChannel.pipeline().addLast(dtlsHandler);
                        dtlsHandler.handshakeFuture().addListener(future1 -> {
                            if (future1.isSuccess()) {
                                fwdUdpChannel.pipeline().addLast(new ResHandler(bus));

                                // bind the active forward UDP leg across indices
                                bus.asos.bindFwd(aso, fwdUdpChannel);

                                Channel bindChannel = aso.getBind();
                                if (bindChannel != null) {
                                    ChannelPipeline udpPipeline = bindChannel.pipeline();
                                    // 1. Remove the temporary setup handler from the pipeline (Synchronous)
                                    if (udpPipeline.get("fwdAsoHandler") != null) {
                                        udpPipeline.remove("fwdAsoHandler");
                                    }
                                    UdpRelayHandler relayHandler = udpPipeline.get(UdpRelayHandler.class);
                                    if (relayHandler != null) {

                                        // 2. Advance state to CONNECTED safely
                                        relayHandler.setState(SessionState.CONNECTED);
                                        // 3. Schedule the empty trigger packet to execute the FIFO queue flush.
                                        // This will naturally stream Packet 1 out first, followed by Packets 2 to N.
                                        bindChannel.eventLoop().execute(() ->
                                                relayHandler.channelRead0(
                                                        udpPipeline.firstContext(),
                                                        new DatagramPacket(io.netty.buffer.Unpooled.EMPTY_BUFFER, (InetSocketAddress) fwdUdpChannel.remoteAddress())));
                                    }
                                }
                                // 4. Remove this handshake ack handler from the TCP pipeline
                                ctx.pipeline().remove(this);
                            } else {
                                ctx.fireExceptionCaught(future1.cause());
                                bus.asos.removeAsobyFwdAssociate(ctx.channel());
                            }
                        });

                    } else {
                        ctx.fireExceptionCaught(future.cause());
                        bus.asos.removeAsobyFwdAssociate(ctx.channel());
                    }
                });
            } else {
                ctx.fireExceptionCaught(new Exception("No active ASO mapping found for packet source."));
                bus.asos.removeAsobyFwdAssociate(ctx.channel());
            }
        } else {
            String statusMsg = (res != null) ? res.status().toString() : "Null response packet payload";
            ctx.fireExceptionCaught(new Exception("SOCKS5 UDP_ASSOCIATE Rejected: " + statusMsg));
            ctx.close();
        }
    }
}
