package com.illiad.proxy.handler.v5;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.codec.v5.V5ClientDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;

/**
 * UDP_ASSOCIATE handler facing the upstream SOCKS5 server
 */
public class FwdAsoHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private final ParamBus bus;
    private final Bootstrap b = new Bootstrap();

    public FwdAsoHandler(ParamBus bus) {
        super(false);
        this.bus = bus;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel sc) {
                    }
                })
                // connect to the proxy server, and forward the SOCKS5 UDP_ASSOCIATE command
                .connect(bus.params.getRemoteHost(), bus.params.getRemotePort())
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        Channel ch = future.channel();
                        SslHandler sslHandler = bus.ssl.sslCtx.newHandler(ch.alloc(), bus.params.getRemoteHost(), bus.params.getRemotePort());
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(sslHandler);
                        // Add a listener for the SSL handshake
                        sslHandler.handshakeFuture().addListener(future1 -> {
                            if (future1.isSuccess()) {
                                DefaultSocks5CommandRequest asoReq = new DefaultSocks5CommandRequest(Socks5CommandType.UDP_ASSOCIATE, Socks5AddressType.IPv4, bus.utils.IPV4_ZERO_Addr, bus.utils.IPV4_ZERO_PORT);
                                // backend outbound encoder: standard socks5 command request (UDP_ASSOCIATE)
                                pipeline.addLast(bus.namer.generateName(), bus.v5ClientEncoder)
                                        // backend inbound decoder: socks5 client decoder
                                        .addLast(bus.namer.generateName(), new V5ClientDecoder(bus))
                                        .addLast(bus.namer.generateName(), new FwdAsoAckHandler(bus, packet.retain()))
                                        .channel()
                                        .writeAndFlush(asoReq).addListener((ChannelFutureListener) future2 -> {
                                            if (!future2.isSuccess()) {
                                                ReferenceCountUtil.release(packet);
                                                ctx.fireExceptionCaught(new Exception(future2.cause()));
                                                bus.utils.closeOnFlush(ch);
                                                bus.utils.closeOnFlush(ctx.channel());
                                            }
                                        });
                            } else {
                                ReferenceCountUtil.release(packet);
                                ctx.fireExceptionCaught(new Exception(future1.cause()));
                                bus.utils.closeOnFlush(ch);
                                bus.utils.closeOnFlush(ctx.channel()); // Close the channel on failure
                            }
                        });
                    } else {
                        ReferenceCountUtil.release(packet);
                        // Close the connection if the connection attempt has failed.
                        ctx.fireExceptionCaught(future.cause());
                        bus.utils.closeOnFlush(future.channel());
                        bus.utils.closeOnFlush(ctx.channel());
                    }
                });
    }
}
