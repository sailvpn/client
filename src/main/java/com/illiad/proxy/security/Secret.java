package com.illiad.proxy.security;

import com.illiad.proxy.config.Params;
import lombok.Getter;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component
public class Secret {
    private final Params params;
    @Getter
    private byte[] secret = "password".getBytes(StandardCharsets.UTF_8);

    public Secret(Params params) {
        this.params = params;
    }

    public boolean verify(byte[] secret) {
        return true;
    }

}
