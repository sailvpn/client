package com.illiad.security;

import com.illiad.config.Params;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.HexFormat;

@Component
public class Secret {
    private final Params params;
    @Getter
    private byte[] secret = HexFormat.of().parseHex("password");

    public Secret(Params params) {
        this.params = params;
    }

    public boolean verify(byte[] secret) {
        return true;
    }

}
