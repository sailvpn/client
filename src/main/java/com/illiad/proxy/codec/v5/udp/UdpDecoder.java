package com.illiad.proxy.codec.v5.udp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.factory.PacketFactories;
import org.pcap4j.packet.namednumber.DataLinkType;

import java.util.List;

public class UdpDecoder extends ByteToMessageDecoder {

    private static final int MIN_IPV4_HEADER_SIZE = 20;
    private static final int IPV6_HEADER_SIZE = 40;
    private static final int VERSION_IPV4 = 4;
    private static final int VERSION_IPV6 = 6;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) {
        if (byteBuf == null || !byteBuf.isReadable() || byteBuf.readableBytes() < 1) {
            return;
        }
        while (byteBuf.readableBytes() > 0) {
            int firstByte = byteBuf.getByte(byteBuf.readerIndex());
            int version = (firstByte >> 4) & 0x0F;

            switch (version) {
                case VERSION_IPV4:
                    IpV4Packet ipv4 = parseIpV4Packet(byteBuf);
                    if (ipv4 != null) {
                        out.add(ipv4);
                    } else {
                        System.out.println("Failed to parse IPv4 packet or insufficient data, stopping.");
                        return;
                    }
                    break;
                case VERSION_IPV6:
                    IpV6Packet ipv6 = parseIpV6Packet(byteBuf);
                    if (ipv6 != null) {
                        out.add(ipv6);
                    } else {
                        System.out.println("Failed to parse IPv6 packet or insufficient data, stopping.");
                        return;
                    }
                    break;
                default:
                    System.out.println("Unknown IP version: " + version + ". Consuming 1 byte and trying again or stopping.");
                    return;
            }
        }
        if (ctx != null) {
            ctx.fireChannelRead(out);
        }
        try {
            super.channelReadComplete(ctx);
        } catch (Exception ignored) {}
    }

    private IpV4Packet parseIpV4Packet(ByteBuf byteBuf) {
        if (byteBuf.readableBytes() < MIN_IPV4_HEADER_SIZE) {
            System.out.println("Not enough data for minimal IPv4 header. Readable: " + byteBuf.readableBytes());
            return null;
        }
        int ihl = byteBuf.getByte(byteBuf.readerIndex()) & 0x0F;
        int headerLength = ihl * 4;
        if (headerLength < MIN_IPV4_HEADER_SIZE) {
            System.out.println("Invalid IPv4 IHL: " + ihl + ". Calculated header length: " + headerLength + " bytes.");
            return null;
        }
        if (byteBuf.readableBytes() < 4) {
            System.out.println("Not enough data to read IPv4 total length field.");
            return null;
        }
        int totalLength = byteBuf.getUnsignedShort(byteBuf.readerIndex() + 2);
        if (totalLength < headerLength) {
            System.out.println("Invalid IPv4 total length: " + totalLength + ". Header length: " + headerLength + ".");
            return null;
        }
        if (byteBuf.readableBytes() < totalLength) {
            System.out.println("Not enough data for declared IPv4 total length. Readable: " + byteBuf.readableBytes() + ", Needed: " + totalLength);
            return null;
        }
        byte[] packetBytes = new byte[totalLength];
        byteBuf.readBytes(packetBytes);
        try {
            Packet pcapPacket = PacketFactories.getFactory(Packet.class, DataLinkType.class)
                    .newInstance(packetBytes, 0, packetBytes.length, DataLinkType.RAW);
            if (pcapPacket instanceof IpV4Packet) {
                return (IpV4Packet) pcapPacket;
            } else if (pcapPacket != null && pcapPacket.contains(IpV4Packet.class)) {
                return pcapPacket.get(IpV4Packet.class);
            } else if (pcapPacket instanceof UnknownPacket) {
                System.out.println("Pcap4j parsed IPv4 data as UnknownPacket. Hex: " + ByteBufUtil.hexDump(packetBytes));
            } else {
                System.out.println("Pcap4j failed to parse IPv4 packet or parsed as wrong type: " + (pcapPacket != null ? pcapPacket.getClass().getSimpleName() : "null"));
            }
        } catch (Exception e) {
            System.out.println("Error parsing IPv4 packet with Pcap4j: " + e.getMessage());
        }
        return null;
    }

    private IpV6Packet parseIpV6Packet(ByteBuf byteBuf) {
        if (byteBuf.readableBytes() < IPV6_HEADER_SIZE) {
            System.out.println("Not enough data for fixed IPv6 header. Readable: " + byteBuf.readableBytes() + ", Needed: " + IPV6_HEADER_SIZE);
            return null;
        }
        int payloadLength = byteBuf.getUnsignedShort(byteBuf.readerIndex() + 4);
        int totalPacketLength = IPV6_HEADER_SIZE + payloadLength;
        if (totalPacketLength > 65535 + IPV6_HEADER_SIZE) {
            System.out.println("Calculated IPv6 total packet length is excessive: " + totalPacketLength + ". Payload length: " + payloadLength);
            return null;
        }
        if (byteBuf.readableBytes() < totalPacketLength) {
            System.out.println("Not enough data for declared IPv6 total packet length. Readable: " + byteBuf.readableBytes() + ", Needed: " + totalPacketLength);
            return null;
        }
        byte[] packetBytes = new byte[totalPacketLength];
        byteBuf.readBytes(packetBytes);
        try {
            Packet pcapPacket = PacketFactories.getFactory(Packet.class, DataLinkType.class)
                    .newInstance(packetBytes, 0, packetBytes.length, DataLinkType.RAW);
            if (pcapPacket instanceof IpV6Packet) {
                return (IpV6Packet) pcapPacket;
            } else if (pcapPacket != null && pcapPacket.contains(IpV6Packet.class)) {
                return pcapPacket.get(IpV6Packet.class);
            } else if (pcapPacket instanceof UnknownPacket) {
                System.out.println("Pcap4j parsed IPv6 data as UnknownPacket. Hex: " + ByteBufUtil.hexDump(packetBytes));
            } else {
                System.out.println("Pcap4j failed to parse IPv6 packet or parsed as wrong type: " + (pcapPacket != null ? pcapPacket.getClass().getSimpleName() : "null"));
            }
        } catch (Exception e) {
            System.out.println("Error parsing IPv6 packet with Pcap4j: " + e.getMessage());
        }
        return null;
    }

}
