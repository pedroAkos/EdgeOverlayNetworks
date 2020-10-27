package protocols.overlays.cyclon.requests;

import babel.generic.ProtoReply;
import network.data.Host;

import java.util.HashSet;
import java.util.Set;

public class MembershipReply extends ProtoReply {

    public static final short REPLY_ID = 421;

    private final Set<Host> peers;

    public MembershipReply(Set<Host> peers) {
        super(REPLY_ID);
        this.peers = new HashSet<>(peers);
    }

    public Set<Host> getPeers() {
        return peers;
    }
}
