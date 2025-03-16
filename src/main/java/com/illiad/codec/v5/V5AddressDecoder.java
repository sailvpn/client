package com.illiad.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import org.springframework.stereotype.Component;

/**
 * Decodes a SOCKS5 address field into its string representation.
 */
@Component
public class V5AddressDecoder {
    private static final int IPv6_LEN = 16;

    public String decodeAddress(Socks5AddressType addrType, ByteBuf in) throws DecoderException {
        if (addrType == Socks5AddressType.IPv4) {
            return NetUtil.intToIpAddress(ByteBufUtil.readIntBE(in));
        }
        if (addrType == Socks5AddressType.DOMAIN) {
            final int length = in.readUnsignedByte();
            final String domain = in.toString(in.readerIndex(), length, CharsetUtil.US_ASCII);
            in.skipBytes(length);
            return domain;
        }
        if (addrType == Socks5AddressType.IPv6) {
            if (in.hasArray()) {
                final int readerIdx = in.readerIndex();
                in.readerIndex(readerIdx + IPv6_LEN);
                return NetUtil.bytesToIpAddress(in.array(), in.arrayOffset() + readerIdx, IPv6_LEN);
            } else {
                byte[] tmp = new byte[IPv6_LEN];
                in.readBytes(tmp);
                return NetUtil.bytesToIpAddress(tmp);
            }
        }
        throw new DecoderException("unsupported address type: " + (addrType.byteValue() & 0xFF));

    }

}
