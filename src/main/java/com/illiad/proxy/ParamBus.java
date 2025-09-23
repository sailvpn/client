package com.illiad.proxy;

import com.illiad.proxy.codec.v5.V5AddressDecoder;
import com.illiad.proxy.codec.v5.V5ClientEncoder;
import com.illiad.proxy.codec.v5.V5ServerEncoder;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.handler.Utils;
import com.illiad.proxy.handler.udp.Asos;
import com.illiad.proxy.security.Dtls;
import com.illiad.proxy.security.Secret;
import com.illiad.proxy.security.Ssl;
import org.springframework.stereotype.Component;

/**
 * this is a helper to hold all the parameters (singleton objects)
 * the itention is to simplify class constructor
 */
@Component
public class ParamBus {
    public final Params params;
    public final HandlerNamer namer;
    public final V5ServerEncoder v5ServerEncoder;
    public final V5ClientEncoder v5ClientEncoder;
    public final V5AddressDecoder v5AddressDecoder;
    public final Ssl ssl;
    public final Dtls dtls;
    public final Secret secret;
    public final Asos asos;
    public final Utils utils;

    public ParamBus(Params params, HandlerNamer namer, V5ServerEncoder v5ServerEncoder, V5ClientEncoder v5ClientEncoder, V5AddressDecoder v5AddressDecoder, Ssl ssl, Dtls dtls, Secret secret, Asos asos, Utils utils) {
        this.params = params;
        this.namer = namer;
        this.v5ServerEncoder = v5ServerEncoder;
        this.v5ClientEncoder = v5ClientEncoder;
        this.v5AddressDecoder = v5AddressDecoder;
        this.ssl = ssl;
        this.dtls = dtls;
        this.secret = secret;
        this.asos = asos;
        this.utils = utils;
    }
}
