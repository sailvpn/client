package com.illiad.proxy.security;

import com.illiad.proxy.config.Params;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
public class DefaultSecret implements Secret {
    private final Params params;

    public DefaultSecret(Params params) {
        this.params = params;
    }

    @Override
    public byte[] getSecret() {
        return params.getSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean verify(byte[] secret) {
        return Arrays.equals(secret, this.getSecret());
    }

}
