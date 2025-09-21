package com.illiad.proxy.handler.udp;

import io.netty.channel.Channel;
import lombok.Data;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

@Data
public class Aso {

    private final Channel associate;
    private final Channel bind;
    // represent the originasl socket address (IP + port) of UDP packet
    private InetSocketAddress source;
    // tcp associate to the 2nd leg relay
    private Channel fwdAssociate;
    // UDP could be 1 source to multiple destination since it is connectionless
    private List<Channel> forwards = new ArrayList<>();

    public Aso(Channel associate, Channel bind) {
        this.associate = associate;
        this.bind = bind;
    }
}
