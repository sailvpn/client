package com.illiad.proxy.handler.v5.udp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class Asos {
    private final List<Aso> pool = new ArrayList<>();

    public void addAso(Aso aso) {

        if (aso != null) {
            // recycle previous/expired aso under the same source and release rerources
            InetSocketAddress source = aso.getSource();
            if (source != null) {
                Aso previous = removeAsoBySource(source);
                if (previous != null) {
                    Channel oldAssociate = previous.getAssociate();
                    if (oldAssociate != null && oldAssociate.isActive()) {
                        oldAssociate.close();
                    }
                    Channel oldBind = previous.getBind();
                    if (oldBind != null && oldBind.isActive()) {
                        oldBind.close();
                    }
                    // close all forwards associates with this source
                    for (Channel forward : aso.getForwards()) {
                        if (forward != null && forward.isActive()) {
                            forward.close();
                        }
                    }
                }
            }
            pool.add(aso);
        }

    }

    public Aso getAsoByBind(Channel bind) {

        if (bind != null) {
            ChannelId id = bind.id();
            for (Aso aso : pool) {
                if (id.equals(aso.getBind().id())) {
                    return aso;
                }
            }
        }
        return null;
    }

    public Aso getAsoBySource(InetSocketAddress source) {

        if (source != null) {
            for (Aso aso : pool) {
                if (source.equals(aso.getSource())) {
                    return aso;
                }
            }
        }
        return null;
    }

    public Aso getAsobyForward(Channel forward) {
        if (forward != null) {
            ChannelId id = forward.id();
            for (Aso aso : pool) {
                for (Channel fwd : aso.getForwards()) {
                    if (id.equals(fwd.id())) {
                        return aso;
                    }
                }
            }
        }
        return null;
    }

    public Aso removeAsoByBind(Channel bind) {

        if (bind != null) {
            ChannelId channelId = bind.id();
            Iterator<Aso> it = pool.iterator();
            while (it.hasNext()) {
                Aso aso = it.next();
                Channel ch = aso.getBind();
                if (ch != null && channelId.equals(ch.id())) {
                    it.remove();
                    return aso;
                }
            }
        }
        return null;
    }

    public Aso removeAsoBySource(InetSocketAddress source) {

        if (source != null) {
            Iterator<Aso> it = pool.iterator();
            while (it.hasNext()) {
                Aso aso = it.next();
                if (source.equals(aso.getSource())) {
                    it.remove();
                    return aso;
                }
            }
        }
        return null;
    }

    public Aso removeAsobyAssociate(Channel associate) {
        if (associate != null) {
            ChannelId id = associate.id();
            Iterator<Aso> it = pool.iterator();
            while (it.hasNext()) {
                Aso aso = it.next();
                if (id.equals(aso.getAssociate().id())) {
                    it.remove();
                    return aso;
                }
            }
        }
        return null;
    }

}
