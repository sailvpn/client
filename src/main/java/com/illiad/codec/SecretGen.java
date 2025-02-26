package com.illiad.codec;

import com.illiad.proxy.Params;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.HexFormat;

@Component
@Data
public class SecretGen {
    private Params params;
    private byte[] secret = HexFormat.of().parseHex("password");

    public SecretGen(Params params) {

    }

}
