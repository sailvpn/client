package com.illiad.proxy.handler.udp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.springframework.stereotype.Component;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Asos {

    private final Map<InetSocketAddress, Aso> sourceIndex = new ConcurrentHashMap<>();
    private final Map<ChannelId, Aso> bindIndex = new ConcurrentHashMap<>();
    private final Map<ChannelId, Aso> associateIndex = new ConcurrentHashMap<>();
    private final Map<ChannelId, Aso> fwdAssociateIndex = new ConcurrentHashMap<>();
    private final Map<ChannelId, Aso> forwardChannelIndex = new ConcurrentHashMap<>();

    // the init of Aso
    public boolean initAso(Channel associate, Channel bind) {
        if (associate == null || bind == null) return false;

        final Aso aso = new Aso(associate, bind);
        associateIndex.put(associate.id(), aso);
        bindIndex.put(bind.id(), aso);
        return true;
    }

    /**
     * Atomically maps the newly discovered UDP client source address to the session registry.
     * This keeps mapping logic contained inside the registry component where it belongs.
     */
    public void bindSource(Aso aso, InetSocketAddress sourceAddress) {
        if (aso == null || sourceAddress == null) return;

        // Check if the source is already bound to prevent redundant map writes
        if (aso.getSource() == null) {
            aso.setSource(sourceAddress);

            // Safely update the index inside the ConcurrentHashMap
            sourceIndex.put(sourceAddress, aso);
        }
    }

    /**
     * Maps the newly connected upstream TCP control channel to the session registry.
     * Safely closes and evicts any hanging previous connections to ensure clean self-healing.
     */
    public void bindFwdAssociate(Aso aso, Channel fwdAssociateChannel) {
        if (aso == null || fwdAssociateChannel == null) return;

        // 1. Fetch the previous connection leg reference
        Channel oldFwdAssociate = aso.getFwdAssociate();
        if (oldFwdAssociate != null) {
            // Remove its lookup tracking index immediately
            fwdAssociateIndex.remove(oldFwdAssociate.id());

            // If it's still running or hanging in a half-dead state, force a clean close
            if (oldFwdAssociate.isActive() || oldFwdAssociate.isOpen()) {
                oldFwdAssociate.close();
            }
        }

        // 2. Bind the new active connection leg references cleanly
        aso.setFwdAssociate(fwdAssociateChannel);
        fwdAssociateIndex.put(fwdAssociateChannel.id(), aso);
    }


    public void registerForwardChannel(Aso aso, Channel forwardChannel) {
        if (aso != null && forwardChannel != null) {
            aso.setForward(forwardChannel);
            forwardChannelIndex.put(forwardChannel.id(), aso);
        }
    }

    public void unbindForward(Aso aso, Channel forward) {
        if (aso != null && forward != null) {
            if (forward.isActive() && forward.isOpen()) {
                forward.close();
            }
            aso.setForward(null);
            forwardChannelIndex.remove(forward.id());
        }

    }

    public Aso getAsoByBind(Channel bind) {
        return bind != null ? bindIndex.get(bind.id()) : null;
    }

    public Aso getAsoBySource(InetSocketAddress source) {
        return source != null ? sourceIndex.get(source) : null;
    }

    public Aso getAsobyForward(Channel forward) {
        return forward != null ? forwardChannelIndex.get(forward.id()) : null;
    }

    public Aso getAsobyFwdAssociate(Channel fwAssociate) {
        return fwAssociate != null ? fwdAssociateIndex.get(fwAssociate.id()) : null;
    }

    public Aso removeAsoByBind(Channel bind) {
        if (bind == null) return null;
        Aso aso = bindIndex.remove(bind.id());
        if (aso != null) {
            if (aso.getSource() != null) sourceIndex.remove(aso.getSource());
            cleanIndexesAndClose(aso);
        }
        return aso;
    }

    public Aso removeAsobyAssociate(Channel associate) {
        if (associate == null) return null;
        Aso aso = associateIndex.remove(associate.id());
        if (aso != null) {
            if (aso.getSource() != null) sourceIndex.remove(aso.getSource());
            cleanIndexesAndClose(aso);
        }
        return aso;
    }

    public Aso removeAsobyFwdAssociate(Channel fwdAssociate) {
        if (fwdAssociate == null) return null;
        Aso aso = fwdAssociateIndex.remove(fwdAssociate.id());
        if (aso != null) {
            if (aso.getSource() != null) sourceIndex.remove(aso.getSource());
            cleanIndexesAndClose(aso);
        }
        return aso;
    }

    public Aso removeAsoBySource(InetSocketAddress source) {
        if (source == null) return null;
        Aso aso = sourceIndex.remove(source);
        if (aso != null) {
            cleanIndexesAndClose(aso);
        }
        return aso;
    }

    private void cleanIndexesAndClose(Aso aso) {
        if (aso.getBind() != null) bindIndex.remove(aso.getBind().id());
        if (aso.getAssociate() != null) associateIndex.remove(aso.getAssociate().id());
        if (aso.getFwdAssociate() != null) fwdAssociateIndex.remove(aso.getFwdAssociate().id());
        if (aso.getForward() != null) forwardChannelIndex.remove(aso.getForward().id());

        // Execute clean, non-leaking teardown of the entire session context
        aso.closeAll();
    }
}

