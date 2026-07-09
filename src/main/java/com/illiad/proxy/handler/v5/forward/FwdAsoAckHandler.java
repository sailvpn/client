package com.illiad.proxy.handler.v5.forward;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.dtls.DtlsHandler;
import com.illiad.proxy.handler.udp.Aso;
import com.illiad.proxy.handler.udp.ResHandler;
import com.illiad.proxy.handler.udp.UdpRelayHandler;
import com.illiad.proxy.handler.udp.SessionState;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.ReferenceCountUtil;

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

                ctx.channel().closeFuture().addListener((ChannelFutureListener) closeFuture ->
                        bus.asos.debindFwdAssociate(aso, ctx.channel()));

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
                        .handler(new ChannelInitializer<DatagramChannel>() {
                            @Override
                            protected void initChannel(DatagramChannel ch) {
                                // Added downstream handlers post-handshake setup
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

                                // Bind the active forward UDP leg across registry indices
                                bus.asos.bindForward(aso, fwdUdpChannel);

                                Channel bindChannel = aso.getBind();
                                if (bindChannel != null) {
                                    // SAFELY execute the pipeline state changes inside the native UDP Loop Thread
                                    bindChannel.eventLoop().execute(() -> {
                                        UdpRelayHandler relayHandler = bindChannel.pipeline().get(UdpRelayHandler.class);
                                        if (relayHandler != null) {
                                            relayHandler.setState(SessionState.CONNECTED);
                                            relayHandler.flushQueue(aso);
                                        }
                                    });
                                }
                            } else {
                                // DTLS Handshake Failed: Tear down the newly allocated UDP Channel socket!
                                fwdUdpChannel.close();
                                handleFailure(ctx, aso, future1.cause());
                            }
                        });

                    } else {
                        // Datagram connect failed
                        handleFailure(ctx, aso, future.cause());
                    }
                });
            } else {
                ctx.fireExceptionCaught(new Exception("No active ASO mapping found for packet source."));
                ctx.close();
            }
        } else {
            String statusMsg = (res != null) ? res.status().toString() : "Null response packet payload";
            ctx.fireExceptionCaught(new Exception("Server SOCKS5 UDP_ASSOCIATE Rejected...: " + statusMsg));
            ctx.close();
        }
    }

    /**
     * Centralized cleanup to prevent descriptor leaks, drain queues, and revert state machines safely.
     */
    private void handleFailure(ChannelHandlerContext ctx, Aso aso, Throwable cause) {
        ctx.fireExceptionCaught(cause);
        bus.asos.removeAsobyFwdAssociate(ctx.channel());

        Channel bindChannel = aso.getBind();
        if (bindChannel != null && bindChannel.isOpen()) {
            bindChannel.eventLoop().execute(() -> {
                // Drop any accumulated client data to free off-heap direct memory instantly
                ByteBuf byteBuf;
                while ((byteBuf = aso.getPacketBuffer().poll()) != null) {
                    ReferenceCountUtil.release(byteBuf);
                }

                UdpRelayHandler relayHandler = bindChannel.pipeline().get(UdpRelayHandler.class);
                if (relayHandler != null) {
                    relayHandler.setState(SessionState.DISCONNECTED);
                }
            });
        }
    }
}
