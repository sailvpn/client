package com.illiad.proxy.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.util.NetUtil;
import org.springframework.stereotype.Component;

import java.net.*;

@Component
public class Utils {

    public final String IPV4_ZERO_Addr = "0.0.0.0";
    public final int IPV4_ZERO_PORT = 0;
    public final String associateFailed = "Creating binding failed";

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    public void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    public Socks5AddressType addressType(String address) {
        if (NetUtil.isValidIpV4Address(address)) {
            return Socks5AddressType.IPv4;
        } else if (NetUtil.isValidIpV6Address(address)) {
            return Socks5AddressType.IPv6;
        } else {
            return Socks5AddressType.DOMAIN;
        }
    }

    public InetSocketAddress parseAddress(ByteBuf buf, byte atyp) throws UnknownHostException {
        if (atyp == 0x01) { // IPv4
            byte[] ip = new byte[4];
            buf.readBytes(ip);
            int port = buf.readUnsignedShort();
            return new InetSocketAddress(
                    (ip[0] & 0xFF) + "." + (ip[1] & 0xFF) + "." + (ip[2] & 0xFF) + "." + (ip[3] & 0xFF),
                    port
            );
        } else if (atyp == 0x03) { // Domain
            int len = buf.readByte();
            byte[] domain = new byte[len];
            buf.readBytes(domain);
            int port = buf.readUnsignedShort();
            return new InetSocketAddress(new String(domain), port);
        } else if (atyp == 0x04) { // IPv6
            byte[] ip = new byte[16];
            buf.readBytes(ip);
            int port = buf.readUnsignedShort();
            return new InetSocketAddress(InetAddress.getByAddress(ip), port);
        }
        throw new IllegalArgumentException("Unknown ATYP: " + atyp);
    }

    public void createSocks5UdpHeader(ByteBuf buf, InetSocketAddress socketAddr) throws UnknownHostException {

        // 1. RSV (Reserved) - 2 bytes (0x0000)
        buf.writeShort(0x0000);

        // 2. FRAG (Fragment) - 1 byte (0x00)
        buf.writeByte(0x00);

        // 3. ATYP (Address Type) and DST.ADDR (Destination Address)
        InetAddress address = socketAddr.getAddress();
        if (address instanceof Inet4Address) {
            buf.writeByte(0x01); // IPv4 address type
            buf.writeBytes(address.getAddress()); // Write the 4-byte IPv4 address
        } else if (address instanceof Inet6Address) {
            buf.writeByte(0x04); // IPv6 address type
            buf.writeBytes(address.getAddress()); // Write the 16-byte IPv6 address
        } else {
            // Handle domain name (ATYP = 0x03)
            buf.writeByte(0x03); // Domain name address type
            byte[] domainBytes = address.getAddress();
            buf.writeByte(domainBytes.length); // Length of domain name
            buf.writeBytes(domainBytes); // Domain name bytes
            throw new UnsupportedOperationException("Domain name addressing not fully implemented in this example.");
        }

        // 4. DST.PORT (Destination Port) - 2 bytes
        buf.writeShort(socketAddr.getPort());

    }
}
