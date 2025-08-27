package com.illiad.proxy.handler.v5;
import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.v5.udp.Aso;
import com.illiad.proxy.handler.v5.udp.ResHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

public class FwdAsoAckHandler extends SimpleChannelInboundHandler<Socks5CommandResponse> {

    private final Bootstrap fwdBootStrap = new Bootstrap();
    private final ParamBus bus;
    private final DatagramPacket packet;

    public FwdAsoAckHandler(ParamBus bus, DatagramPacket packet) {
        this.bus = bus;
        this.packet = packet;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5CommandResponse res) throws ExecutionException, InterruptedException {

        if (packet != null) {
            if (res != null) {
                if (res.status() == Socks5CommandStatus.SUCCESS) {

                    Aso aso = bus.asos.getAsoBySource(packet.sender());
                    if (aso != null) {
                        // associate the forward associate (TCP) channel
                        aso.setFwdAssociate(ctx.channel());
                        final ChannelPipeline pipeline = ctx.pipeline();
                        pipeline.addLast(new FwdCloseHandler(bus));

                        String prefix = bus.namer.getPrefix();
                        // remove all handlers except SslHandler from backendPipeline
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
                                        ch.pipeline().addLast(new ResHandler(bus));
                                    }
                                })
                                .connect(res.bndAddr(), res.bndPort())
                                .addListener(new ChannelFutureListener() {
                                    @Override
                                    public void operationComplete(ChannelFuture future) throws Exception {
                                        if (future.isSuccess()) {
                                            // The connection was successful, and the channel is now active.
                                            Channel fwdUdpChannel = future.channel();
                                            //associate forward Udp Channel
                                            aso.getForwards().add(fwdUdpChannel);
                                            // new Udp Packet with forward remote address (2nd leg's bind) as recepient, 1st leg's bind as sender
                                            DatagramPacket fwdUdpPacket = new DatagramPacket(packet.content(), (InetSocketAddress) fwdUdpChannel.remoteAddress(), (InetSocketAddress) aso.getBind().localAddress());
                                            fwdUdpChannel.writeAndFlush(fwdUdpPacket);

                                        } else {
                                            // The connection failed.
                                            System.err.println("Connection failed: " + future.cause());
                                        }
                                    }
                                });
                    }
                } else {
                    ctx.fireExceptionCaught(new Exception(res.status().toString()));
                    bus.utils.closeOnFlush(ctx.channel());
                }
            }
        }
    }


}

