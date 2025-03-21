package com.illiad.proxy.codec.v4;

import io.netty.util.internal.UnstableApi;

/**
 *  {@link V4ServerDecoder} state
 */
@UnstableApi
public enum State {
    START,
    READ_USERID,
    READ_DOMAIN,
    SUCCESS,
    FAILURE
}