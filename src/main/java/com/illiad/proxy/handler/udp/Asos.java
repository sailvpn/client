package com.illiad.proxy.handler.udp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import jakarta.annotation.PreDestroy;
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
    private final Map<ChannelId, Aso> forwardIndex = new ConcurrentHashMap<>();

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
    public void bindFwdAssociate(Aso aso, Channel fwdAssociate) {

        if (aso != null && fwdAssociate != null) {
            // 1. Fetch the previous connection leg reference
            Channel oldFwdAssociate = aso.getFwdAssociate();
            // 2. Bind the new active connection leg references cleanly
            aso.setFwdAssociate(fwdAssociate);
            fwdAssociateIndex.put(fwdAssociate.id(), aso);
            if (oldFwdAssociate != null) {
                // Remove its lookup tracking index immediately
                fwdAssociateIndex.remove(oldFwdAssociate.id());

                // If it's still running or hanging in a half-dead state, force a clean close
                if (oldFwdAssociate.isOpen()) {
                    oldFwdAssociate.close();
                }
            }
        }
    }

    public void debindFwdAssociate(Aso aso, Channel fwdAssociate) {
        if (aso != null && fwdAssociate != null) {
            fwdAssociateIndex.remove(fwdAssociate.id());
            aso.setFwdAssociate(null);
            if (fwdAssociate.isOpen()) {
                fwdAssociate.close();
            }
        }
    }


    public void bindForward(Aso aso, Channel forward) {
        if (aso != null && forward != null) {
            Channel oldFwd = aso.getForward();
            aso.setForward(forward);
            forwardIndex.put(forward.id(), aso);
            if (oldFwd != null) {
                forwardIndex.remove(oldFwd.id());
                if (oldFwd.isOpen()) {
                    oldFwd.close();
                }
            }
        }
    }

    public void debindForward(Aso aso, Channel forward) {
        if (aso != null && forward != null) {
            forwardIndex.remove(forward.id());
            aso.setForward(null);
            if (forward.isOpen()) {
                forward.close();
            }
        }
    }

    public Aso getAsoByBind(Channel bind) {
        return bind != null ? bindIndex.get(bind.id()) : null;
    }

    public Aso getAsoBySource(InetSocketAddress source) {
        return source != null ? sourceIndex.get(source) : null;
    }

    public Aso getAsobyForward(Channel forward) {
        return forward != null ? forwardIndex.get(forward.id()) : null;
    }

    public Aso getAsobyFwdAssociate(Channel fwAssociate) {
        return fwAssociate != null ? fwdAssociateIndex.get(fwAssociate.id()) : null;
    }

    public Aso removeAsoByBind(Channel bind) {
        if (bind == null) return null;
        Aso aso = bindIndex.get(bind.id());
        if (aso != null) {
            cleanIndexesAndClose(aso);
        }
        return aso;
    }

    public Aso removeAsobyAssociate(Channel associate) {
        if (associate == null) return null;
        Aso aso = associateIndex.get(associate.id());
        if (aso != null) {
            cleanIndexesAndClose(aso);
        }
        return aso;
    }

    public Aso removeAsobyFwdAssociate(Channel fwdAssociate) {
        if (fwdAssociate == null) return null;
        Aso aso = fwdAssociateIndex.get(fwdAssociate.id());
        if (aso != null) {
            cleanIndexesAndClose(aso);
        }
        return aso;
    }

    public Aso removeAsoBySource(InetSocketAddress source) {
        if (source == null) return null;
        Aso aso = sourceIndex.get(source);
        if (aso != null) {
            cleanIndexesAndClose(aso);
        }
        return aso;
    }

    private void cleanIndexesAndClose(Aso aso) {
        if (aso.getSource() != null) sourceIndex.remove(aso.getSource());
        if (aso.getBind() != null) bindIndex.remove(aso.getBind().id());
        if (aso.getAssociate() != null) associateIndex.remove(aso.getAssociate().id());
        if (aso.getFwdAssociate() != null) fwdAssociateIndex.remove(aso.getFwdAssociate().id());
        if (aso.getForward() != null) forwardIndex.remove(aso.getForward().id());

        // Execute clean, non-leaking teardown of the entire session context
        aso.closeAll();
    }

    @PreDestroy
    public void preDestroy() {
        associateIndex.values().forEach(aso -> {
            if (aso != null) aso.closeAll();
        });
        associateIndex.clear();
        bindIndex.values().forEach(aso -> {
            if (aso != null) aso.closeAll();
        });
        bindIndex.clear();
        fwdAssociateIndex.values().forEach(aso -> {
            if (aso != null) aso.closeAll();
        });
        fwdAssociateIndex.clear();
        forwardIndex.values().forEach(aso -> {
            if (aso != null) aso.closeAll();
        });
        forwardIndex.clear();
    }
}

