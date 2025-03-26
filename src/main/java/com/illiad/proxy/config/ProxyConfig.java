package com.illiad.proxy.config;

import com.illiad.proxy.codec.Header;
import com.illiad.proxy.codec.HeaderEncoder;
import com.illiad.proxy.codec.v4.V4ClientEncoder;
import com.illiad.proxy.codec.v4.V4ServerEncoder;
import com.illiad.proxy.codec.v5.V5AddressEncoder;
import com.illiad.proxy.codec.v5.V5ClientEncoder;
import com.illiad.proxy.codec.v5.V5ServerEncoder;
import com.illiad.proxy.handler.Utils;
import com.illiad.proxy.handler.v4.V4CommandHandler;
import com.illiad.proxy.handler.v4.V4ConnectHandler;
import com.illiad.proxy.handler.v5.V5CommandHandler;
import com.illiad.proxy.handler.v5.V5ConnectHandler;
import com.illiad.proxy.security.DefaultSecret;
import com.illiad.proxy.security.Secret;
import com.illiad.proxy.security.Ssl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProxyConfig {

    @Bean
    public Params params() {
        return new Params();
    }

    @Bean
    public V5AddressEncoder v5AddressEncoder() {
        return new V5AddressEncoder();
    }

    @Bean
    public V4ServerEncoder v4ServerEncoder() {
        return new V4ServerEncoder();
    }

    @Bean
    public Header header(Params params) {
        return new Header(params);
    }

    @Bean
    public Secret secret(Params params) {
        return new DefaultSecret(params);
    }

    @Bean
    public HeaderEncoder headerEncoder(Secret secret, Header header) {
        return new HeaderEncoder(secret, header);
    }

    @Bean
    public V4ClientEncoder v4ClientEncoder() {
        return new V4ClientEncoder();
    }

    @Bean
    public V4ConnectHandler v4ConnectHandler(Ssl ssl, Params params, HeaderEncoder headerEncoder, V4ClientEncoder v4ClientEncoder, Utils utils) {
        return new V4ConnectHandler(ssl,params, headerEncoder, v4ClientEncoder, utils);
    }

    @Bean
    public Utils utils() {
        return new Utils();
    }

    @Bean
    public V4CommandHandler v4CommandHandler(V4ConnectHandler connectHandler, Utils utils) {
        return new V4CommandHandler(connectHandler, utils);
    }

    @Bean
    public V5ServerEncoder v5ServerEncoder() {
        return new V5ServerEncoder(new V5AddressEncoder());
    }

    @Bean
    public V5ClientEncoder v5ClientEncoder(V5AddressEncoder v5AddressEncoder) {
        return new V5ClientEncoder(v5AddressEncoder);
    }

    @Bean
    public V5ConnectHandler v5ConnectHandler(Ssl ssl, Params params, HeaderEncoder headerEncoder, V5ClientEncoder v5ClientEncoder, Utils utils) {
        return new V5ConnectHandler(ssl, params, headerEncoder, v5ClientEncoder, utils);
    }

    @Bean
    public V5CommandHandler v5CommandHandler(V5ConnectHandler connectHandler, Utils utils) {
        return new V5CommandHandler( connectHandler,  utils);
    }
}