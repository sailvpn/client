package com.illiad.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.Socks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;

import java.util.List;

public class ResponseHandler extends ByteToMessageDecoder {

    private final Promise<Channel> promise;

    public ResponseHandler(Promise<Channel> promise) {
        this.promise = promise;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> list) {

        SocksMessage message = (SocksMessage) ObjectUtil.checkNotNull(ObjectUtil.checkNotNull(list, "null object list received").get(0), "null message received");

        Channel frontend = promise.getNow();
        // wite response to frontend
        frontend.writeAndFlush(message).addListener(
                future -> {
                    if (future.isSuccess()) {
                        // put frontend input onhold until both frontend and backend are ready
                        frontend.config().setAutoRead(false);
                        // trigger promise as per message status
                        if (message.version() == SocksVersion.SOCKS5) {
                            if (((Socks5CommandResponse) message).status() == Socks5CommandStatus.SUCCESS) {
                                promise.setSuccess(ctx.channel());
                            } else {
                                promise.setFailure(new Exception(((Socks5CommandResponse) message).status().toString()));
                                ctx.fireExceptionCaught(new Exception(((Socks5CommandResponse) message).status().toString()));
                            }

                        } else if (message.version() == SocksVersion.SOCKS4a) {
                            if (((Socks4CommandResponse) message).status() == Socks4CommandStatus.SUCCESS) {
                                promise.setSuccess(ctx.channel());
                            } else {
                                promise.setFailure(new Exception(((Socks4CommandResponse) message).status().toString()));
                                ctx.fireExceptionCaught(new Exception(((Socks4CommandResponse) message).status().toString()));
                            }

                        } else {
                            promise.setFailure(new Exception(message.version().name()));
                            ctx.fireExceptionCaught(new Exception(message.version().name()));
                        }
                    } else {
                        promise.setFailure(future.cause());
                        ctx.fireExceptionCaught(future.cause());
                    }
                }
        );

        ctx.pipeline().remove(this);
    }

}
