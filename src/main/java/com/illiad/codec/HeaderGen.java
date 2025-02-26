package com.illiad.codec;

import com.illiad.proxy.Params;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class HeaderGen {
    private Params params;
    private final Random random;

    public HeaderGen(Params params){
        this.random = new Random();
    }

    public byte[] header() {
        int offSet = random.nextInt(params.getMax() - params.getMin()) + params.getMin();
        byte[] byteArray = new byte[offSet];
        random.nextBytes(byteArray);
        return byteArray;
    }
}
