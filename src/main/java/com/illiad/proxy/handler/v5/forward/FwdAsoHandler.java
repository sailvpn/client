package com.illiad.proxy.handler.v5.forward;

import com.illiad.proxy.ParamBus;
import com.illiad.proxy.handler.udp.Aso;
import com.illiad.proxy.codec.v5.PseudoResDecoder;
import com.illiad.proxy.codec.v5.V5ClientDecoder;
import com.illiad.proxy.handler.udp.UdpRelayHandler;
import com.illiad.proxy.handler.udp.SessionState;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;

/**
 * UDP_ASSOCIATE handler facing the upstream SOCKS5 server.
 * Extends ChannelInboundHandlerAdapter to cleanly intercept a simple string literal token
 * and launch the asynchronous upstream TCP/DTLS proxy handshake.
 */
public class FwdAsoHandler extends ChannelInboundHandlerAdapter {
    private final ParamBus bus;

    public FwdAsoHandler(ParamBus bus) {
        this.bus = bus;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // Verify this is our intended simple trigger string token
        if (bus.utils.UPSTREAM_SETUP.equals(msg)) {

            // Fetch the overarching session tracker via the inbound UDP binding channel ID
            Aso aso = bus.asos.getAsoByBind(ctx.channel());
            if (aso == null) {
                resetAndPurgeSession(ctx.channel(), null);
                ctx.pipeline().remove(this);
                return;
            }

            Bootstrap cb = new Bootstrap();
            // Reuses Netty's exact network context to map thread execution loops natively
            cb.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel sc) {
                            // Left empty intentionally as handlers are added dynamically post-connect
                        }
                    });

            final Channel udpBindChannel = ctx.channel();

            ctx.pipeline().remove(this);

            // Initiate the proxy connection using the clean bootstrap instance
            cb.connect(bus.params.getRemoteHost(), bus.params.getRemotePort())
                    .addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            Channel ch = future.channel();
                            bus.asos.bindFwdAssociate(aso, ch);

                            String sni = bus.params.getSni();
                            if (sni == null || sni.isEmpty()) {
                                sni = bus.params.getRemoteHost();
                            }

                            SslHandler sslHandler = bus.cert.sslCtx.newHandler(ch.alloc(), sni, bus.params.getRemotePort());
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(sslHandler);

                            sslHandler.handshakeFuture().addListener(future1 -> {
                                if (future1.isSuccess()) {
                                    DefaultSocks5CommandRequest asoReq = new DefaultSocks5CommandRequest(
                                            Socks5CommandType.UDP_ASSOCIATE,
                                            Socks5AddressType.IPv4,
                                            bus.utils.IPV4_ZERO_Addr,
                                            bus.utils.IPV4_ZERO_PORT
                                    );

                                    // Assemble backend logic components inline
                                    pipeline.addFirst(new IdleStateHandler(0, 0, 60), new ChannelDuplexHandler() {
                                        @Override
                                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                            if (evt instanceof IdleStateEvent) {
                                                bus.asos.debindFwdAssociate(aso, ctx.channel());
                                                bus.asos.debindForward(aso, aso.getForward());
                                            } else {
                                                super.userEventTriggered(ctx, evt);
                                            }
                                        }
                                    });

                                    pipeline.addLast(bus.namer.generateName(), bus.v5ClientEncoder)
                                            .addLast(new PseudoResDecoder())
                                            .addLast(bus.namer.generateName(), new V5ClientDecoder(bus))
                                            .addLast(bus.namer.generateName(), new FwdAsoAckHandler(bus))
                                            .channel()
                                            .writeAndFlush(asoReq).addListener((ChannelFutureListener) future2 -> {
                                                if (!future2.isSuccess()) {
                                                    resetAndPurgeSession(udpBindChannel, aso);
                                                    bus.asos.debindFwdAssociate(aso, ch);
                                                }
                                            });
                                } else {
                                    resetAndPurgeSession(udpBindChannel, aso);
                                    bus.asos.debindFwdAssociate(aso, ch);
                                }
                            });
                        } else {
                            resetAndPurgeSession(udpBindChannel, aso);
                            bus.asos.debindFwdAssociate(aso, future.channel());
                        }
                    });
        } else {
            // other unknown object passes through, propagate it down the pipeline untouched
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Reverts the UDP state machine safely and clears out cached packets
     * to eliminate memory leaks and stale tracking buffers on handshake failure.
     */
    private void resetAndPurgeSession(Channel udpBindChannel, Aso aso) {
        if (aso != null) {
            ByteBuf byteBuf;
            while ((byteBuf = aso.getPacketBuffer().poll()) != null) {
                ReferenceCountUtil.release(byteBuf);
            }
        }

        if (udpBindChannel != null && udpBindChannel.isOpen()) {
            udpBindChannel.eventLoop().execute(() -> {
                UdpRelayHandler relayHandler = udpBindChannel.pipeline().get(UdpRelayHandler.class);
                if (relayHandler != null) {
                    relayHandler.setState(SessionState.DISCONNECTED);
                }
            });
        }
    }
}
