package com.illiad.proxy;

import org.springframework.stereotype.Component;
import java.nio.channels.DatagramChannel;

/**
 * this class hold an instance of UDP relay channel on server side
 */
@Component
public class UdpChannel {
    public DatagramChannel dChannel = null;
}
