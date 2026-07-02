package com.illiad.proxy.handler.udp;

import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import lombok.Data;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class Aso {
    private final Channel associate;
    private final Channel bind;
    private InetSocketAddress source;
    private Channel fwdAssociate;
    private Channel forward;

    // A thread-safe queue to buffer packets while the upstream handshake is in-flight
    private final Queue<DatagramPacket> packetBuffer = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean cleaned = new AtomicBoolean(false);

    public Aso(Channel associate, Channel bind) {
        this.associate = associate;
        this.bind = bind;
    }

    /**
     * Clear and close all sockets safely, cleaning up any buffered packets.
     */
    public void closeAll() {
        if (!cleaned.compareAndSet(false, true)) {
            return;
        }

        // Release any stashed buffers to prevent severe direct memory leaks
        DatagramPacket bufferedPacket;
        while ((bufferedPacket = packetBuffer.poll()) != null) {
            ReferenceCountUtil.release(bufferedPacket);
        }

        source = null;

        if (forward != null && forward.isOpen()) {
            forward.close();
        }
        if (fwdAssociate != null && fwdAssociate.isOpen()) {
            fwdAssociate.close();
        }
        if (associate != null && associate.isOpen()) {
            associate.close();
        }
        if (bind != null && bind.isOpen()) {
            bind.close();
        }
    }
}


