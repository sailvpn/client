package com.illiad.proxy.codec;

import com.illiad.proxy.config.Params;
import java.util.Random;
import org.springframework.stereotype.Component;
@Component
public class Header {
    private final Params params;
    private final Random random;

    public Header(Params params){
        this.params = params;
        this.random = new Random();
    }

    public byte[] offset() {
        int length = random.nextInt(params.getMax() - params.getMin()) + params.getMin();
        byte[] byteArray = new byte[length];
        random.nextBytes(byteArray);
        return byteArray;
    }
}
