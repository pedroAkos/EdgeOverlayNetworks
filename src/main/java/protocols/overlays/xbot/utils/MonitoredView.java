package protocols.overlays.xbot.utils;

import network.data.Host;
import protocols.overlays.hyparview.utils.View;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class MonitoredView extends View {

    private final Oracle oracle;
    private final boolean isPassive;

    private Set<Host> pending;

    public MonitoredView(int capacity, Host self, Random rnd, Oracle oracle, boolean isPassive) {
        super(capacity, self, rnd);
        this.oracle = oracle;
        this.isPassive = isPassive;
        this.pending = new HashSet<>();
    }

    @Override
    public Host addPeer(Host peer) {
        Host toDrop = super.addPeer(peer);
        if(super.containsPeer(peer))
            oracle.monitor(peer);
        return toDrop;
    }

    @Override
    public boolean removePeer(Host peer) {
        boolean ret = super.removePeer(peer);
        if (ret)
            oracle.unMonitor(peer);
        return ret;
    }

    @Override
    public Host dropRandom() {
        Host dropped = super.dropRandom();
        if(isPassive)
            oracle.unMonitor(dropped);
        return dropped;
    }

    @Override
    public boolean fullWithPending(Set<Host> pending) {
        Set<Host> all = new HashSet<>(pending);
        all.addAll(this.pending);
        return super.fullWithPending(all);
    }

    public boolean removePeerKeepMonitoring(Host peer) {
        return super.removePeer(peer);
    }

    public void moveToPending(Host peer) {
        if(super.removePeer(peer))
            this.pending.add(peer);
    }

    public void removeFromPending(Host peer) {
        this.pending.remove(peer);
    }

    public boolean isInPending(Host peer) {
        return this.pending.contains(peer);
    }
}
