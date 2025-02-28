package com.illiad.config;

import com.illiad.security.Ssl;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import org.springframework.beans.factory.annotation.Autowired;

public class BackEndInitializer extends ChannelInitializer<SocketChannel> {

    @Autowired
    private Ssl ssl;

    @Autowired
    Params params;

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // Add SSL handler first to encrypt and decrypt everything.
        // In this example, we use a bogus certificate in the server side
        // and accept any invalid certificates in the client side.
        // You will need something more complicated to identify both
        // and server in the real world.
        pipeline.addLast(ssl.sslCtx.newHandler(ch.alloc(), params.remoteHost, params.remotePort));

        // On top of the SSL handler, add the text line codec.

        // and then business logic.

    }
}
