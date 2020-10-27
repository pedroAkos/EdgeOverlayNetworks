package protocols.overlays.hyparview.utils;

import network.data.Host;

import java.util.*;

public interface IView {

    public void setOther(IView other, Set<Host> pending);

    public Host addPeer(Host peer);

    public boolean removePeer(Host peer);

    public boolean containsPeer(Host peer);

    public Host dropRandom();

    public Set<Host> getRandomSample(int sampleSize);

    public Set<Host> getPeers();

    public Host getRandom();

    public Host getRandomDiff(Host from);

    public boolean fullWithPending(Set<Host> pending);

    public boolean isFull();
}
