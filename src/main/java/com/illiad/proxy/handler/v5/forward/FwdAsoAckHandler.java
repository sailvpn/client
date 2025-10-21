package com.illiad.proxy.handler.v5.forward;
import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.dtls.DtlsHandler;
import com.illiad.proxy.handler.udp.Aso;
import com.illiad.proxy.handler.udp.ResHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;

public class FwdAsoAckHandler extends SimpleChannelInboundHandler<Socks5CommandResponse> {

    private final Bootstrap fwdBootStrap = new Bootstrap();
    private final ParamBus bus;
    private final DatagramPacket packet;

    public FwdAsoAckHandler(ParamBus bus, DatagramPacket packet) {
        super(false);
        this.bus = bus;
        this.packet = packet;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5CommandResponse res) {

        if (packet != null && res != null && res.status() == Socks5CommandStatus.SUCCESS) {

            Aso aso = bus.asos.getAsoBySource(packet.sender());
            if (aso != null) {
                // associate the forward associate (TCP) channel
                aso.setFwdAssociate(ctx.channel());
                final ChannelPipeline pipeline = ctx.pipeline();
                pipeline.addLast(new FwdCloseHandler(bus));

                String prefix = bus.namer.getPrefix();
                // remove all handlers except SslHandler from backend Pipeline
                for (String name : pipeline.names()) {
                    if (name.startsWith(prefix)) {
                        pipeline.remove(name);
                    }
                }
                // establish UDP forward to 2nd leg
                fwdBootStrap.group(ctx.channel().eventLoop())
                        .channel(NioDatagramChannel.class)
                        // Enable broadcasting if needed
                        .option(ChannelOption.SO_BROADCAST, true)
                        .handler(new ChannelInitializer<DatagramChannel>() {
                            @Override
                            protected void initChannel(DatagramChannel ch) {
                            }
                        })
                        .connect(res.bndAddr(), res.bndPort())
                        .addListener((ChannelFutureListener) future -> {
                            ReferenceCountUtil.release(res);
                            if (future.isSuccess()) {
                                // The connection was successful, and the channel is now active.
                                Channel fwdUdpChannel = future.channel();
                                DtlsHandler dtlsHandler = new DtlsHandler(bus, (InetSocketAddress) fwdUdpChannel.remoteAddress());

                                fwdUdpChannel.pipeline().addLast(new LoggingHandler(LogLevel.INFO))
                                        .addLast(dtlsHandler);
                                dtlsHandler.handshakeFuture().addListener(future1 -> {
                                    if (future1.isSuccess()) {
                                        // Successfully completed DTLS handshake
                                        fwdUdpChannel.pipeline()
                                                .addLast(new ResHandler(bus));
                                        //associate forward Udp Channel
                                        aso.getForwards().add(fwdUdpChannel);

                                        // new Udp Packet with forward remote address (2nd leg's bind) as recepient, 1st leg's bind as sender
                                        DatagramPacket fwdUdpPacket = new DatagramPacket(packet.content(), (InetSocketAddress) fwdUdpChannel.remoteAddress(), (InetSocketAddress) aso.getBind().localAddress());
                                        fwdUdpChannel.writeAndFlush(fwdUdpPacket)
                                                .addListener(future2 -> {
                                                    if (!future2.isSuccess()) {
                                                        ctx.fireExceptionCaught(future2.cause());
                                                        bus.utils.closeOnFlush(fwdUdpChannel);
                                                    }
                                                });

                                    } else {
                                        ReferenceCountUtil.release(packet);
                                        ctx.fireExceptionCaught(future1.cause());
                                        bus.utils.closeOnFlush(fwdUdpChannel);
                                    }
                                });

                            } else {
                                ReferenceCountUtil.release(packet);
                                ctx.fireExceptionCaught(future.cause());
                                // The connection failed.
                                System.err.println("Connection failed: " + future.cause());
                            }
                        });
            } else {
                ctx.fireExceptionCaught(new Exception(res.status().toString()));
                ReferenceCountUtil.release(packet);
                ReferenceCountUtil.release(res);
                bus.utils.closeOnFlush(ctx.channel());
            }
        } else {
            assert res != null;
            ctx.fireExceptionCaught(new Exception(res.status().toString()));
            ReferenceCountUtil.release(packet);
            ReferenceCountUtil.release(res);
            bus.utils.closeOnFlush(ctx.channel());
        }
    }


}

