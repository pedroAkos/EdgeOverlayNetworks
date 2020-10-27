package protocols.dissemination.plumtree.utils;

import network.data.Host;

public class MessageSource {

    public Host peer;
    public int round;

    public MessageSource(Host peer, int round) {
        this.peer = peer;
        this.round = round;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageSource that = (MessageSource) o;
        return peer.equals(that.peer);
    }

    @Override
    public int hashCode() {
        return peer.hashCode();
    }
}
