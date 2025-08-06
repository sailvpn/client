package com.illiad.proxy;

import io.netty.channel.Channel;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * this class hold an instance of UDP relay channel on server side
 */
@Component
@Data
public class UdpChannel {
    private Channel channel = null;
}
