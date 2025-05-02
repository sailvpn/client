package com.illiad.proxy;

import lombok.Getter;
import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class HandlerNamer {

    private final Random random;
    @Getter
    private final String prefix;

    public HandlerNamer() {
        this.random = new Random();
        this.prefix = generateRandomAlphanumeric(random, 5);
    }

    public String generateName() {
        return  this.prefix + generateRandomAlphanumeric(random, 5);
    }

    private static String generateRandomAlphanumeric(Random random, int length) {
        StringBuilder sb = new StringBuilder(length);
        String alphanumeric = "abcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < length; i++) {
            sb.append(alphanumeric.charAt(random.nextInt(alphanumeric.length())));
        }
        return sb.toString();
    }
}
