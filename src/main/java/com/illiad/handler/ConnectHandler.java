package com.illiad.handler;

import com.illiad.codec.HeaderEncoder;

import com.illiad.config.Params;
import com.illiad.proxy.Utils;
import com.illiad.security.Ssl;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.Socks4ClientDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public final class ConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {

    private final Ssl ssl;
    private final Params params;
    private final HeaderEncoder headerEncoder;
    private final Utils utils;

    public ConnectHandler(Ssl ssl, Params params, HeaderEncoder headerEncoder, Utils utils) {

        this.ssl = ssl;
        this.params = params;
        this.headerEncoder = headerEncoder;
        this.utils = utils;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) {

        try {
            Bootstrap b = new Bootstrap();
            b.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            // Add SSL handler first to encrypt and decrypt everything.
                            // In this example, we use a bogus certificate in the server side
                            // and accept any invalid certificates in the client side.
                            // You will need something more complicated to identify both
                            // and server in the real world.
                            pipeline.addLast(ssl.sslCtx.newHandler(ch.alloc(), params.getRemoteHost(), params.getRemotePort()));

                            // On top of the SSL handler, add the text line codec.
                            // inbound decoder: standard socks5 command response or socks4 response
                            pipeline.addLast(message instanceof Socks5CommandRequest ? new Socks5CommandResponseDecoder() : new Socks4ClientDecoder());
                            // outbound encoder: a header followeed by standard socks5 command request (Connect or UdP)
                            pipeline.addLast(message instanceof Socks5CommandRequest ? Socks5ClientEncoder.DEFAULT : Socks4ClientEncoder.INSTANCE);
                            pipeline.addLast(headerEncoder);
                        }
                    });

            b.connect(params.getRemoteHost(), params.getRemotePort())
                    .sync().channel()
                    .writeAndFlush(message)
                    .sync();

            ctx.pipeline().remove(this);
        } catch (Exception ignored) {

        } finally {
            utils.closeOnFlush(ctx.channel());
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        utils.closeOnFlush(ctx.channel());
    }
}
