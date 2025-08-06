package com.illiad.proxy;

import io.netty.channel.socket.nio.NioDatagramChannel;
import org.springframework.stereotype.Component;

/**
 * this class hold an instance of UDP relay channel on server side
 */
@Component
public class UdpChannel {
    public NioDatagramChannel dChannel = null;
}
