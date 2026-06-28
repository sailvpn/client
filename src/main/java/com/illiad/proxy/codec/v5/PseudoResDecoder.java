package com.illiad.proxy.codec.v5;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

public class PseudoResDecoder extends ByteToMessageDecoder {

    private static final byte[] CRLF = new byte[]{0x0D, 0x0A};

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // 1. Ensure we have at least 2 bytes to safely read the short payload length prefix
        if (in.readableBytes() < 2) {
            return;
        }

        // Peek the length without advancing the reader index yet
        int readerIndex = in.readerIndex();
        short resLen = in.getShort(readerIndex);

        // 2. The full frame consists of 2 bytes (short) + resLen (payload)
        int totalFrameBytes = 2 + resLen;

        // Wait until the entire obfuscation frame arrives in the TCP buffer
        if (in.readableBytes() < totalFrameBytes) {
            return;
        }

        // 3. Calculate the correct position of the trailing CRLF
        // The CRLF is the last two bytes of the resLen payload
        int crIndex = readerIndex + resLen;

        // Validate the CRLF bytes to ensure stream synchronization
        if (CRLF[0] == in.getByte(crIndex) && CRLF[1] == in.getByte(crIndex + 1)) {
            // 4. Advance the reader index past the entire obfuscated junk block
            in.readerIndex(readerIndex + totalFrameBytes);
        }

        // 5. Cleanly self-destruct this handler from the pipeline
        ctx.pipeline().remove(this);

        // 6. If there are trailing bytes left in the buffer (the start of the real TLS stream),
        // forward them to the next handler immediately.
        if (in.isReadable()) {
            ctx.fireChannelRead(in.retain());
        }
    }
}

