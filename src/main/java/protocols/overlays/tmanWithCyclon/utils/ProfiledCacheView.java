package protocols.overlays.tmanWithCyclon.utils;

import network.data.Host;
import protocols.overlays.cyclon.utils.CacheView;
import protocols.overlays.tman.utils.Node;
import protocols.overlays.tman.utils.profile.Profile;

import java.util.*;

public class ProfiledCacheView extends CacheView {

    private final Map<Host, Node> profiles;

    public ProfiledCacheView(int capacity, Random rnd, Host self) {
        super(capacity, rnd, self);
        this.profiles = new HashMap<>(capacity, 1);

    }

    public void addProfile(Host host, Profile profile) {
        if(getCache().contains(host))
            profiles.put(host, new Node(host, profile));
    }

    public void addProfile(Node node) {
        if(getCache().contains(node.getHost()))
            profiles.put(node.getHost(), node);
    }

    @Override
    public boolean removePeer(Host peer) {
        if(super.removePeer(peer))
            return profiles.remove(peer) != null;
        else
            return false;
    }

    public Set<Node> getProfiles() {
        return new HashSet<>(profiles.values());
    }

    public Set<Node> getProfiles(Set<Host> hosts) {
        Map<Host, Node> tmp = new HashMap<>(profiles);
        tmp.keySet().retainAll(hosts);
        return new HashSet<>(tmp.values());
    }

    public Collection<Node> getRandomProfiledSubset(int size) {
        return getProfiles(getRandomSubset(size));
    }

}
