package protocols.overlays.cyclon.utils;

import network.data.Host;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class CacheView {

    protected final Map<Host, MutableInt> cache;
    protected final int capacity;
    private final Random rnd;
    protected final Host self;
    private final Queue<AgedHost> ordered;

    private class AgedHost {
        private Host h;
        private MutableInt age;
        private AgedHost(Host h, MutableInt age) {
            this.age = age;
            this.h = h;
        }

        public Host getHost() {
            return h;
        }

        public int getAge() {
            return age.getValue();
        }

        public void incAge() {
            age.increment();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AgedHost agedHost = (AgedHost) o;
            return h.equals(agedHost.h);
        }

        @Override
        public int hashCode() {
            return h.hashCode();
        }
    }

    public CacheView(int capacity, Random rnd, Host self) {
        this.cache = new HashMap<>(capacity, 1);
        this.capacity = capacity;
        this.rnd = rnd;
        this.self = self;
        this.ordered = new PriorityQueue<>(Comparator.comparing(AgedHost::getAge).reversed());
    }

    @Override
    public String toString() {
        return "View{" +
                "cache=" + cache +
                '}';
    }

    public boolean addPeer(Host peer, int age) {
        if(!peer.equals(self)) {
            if (!cache.containsKey(peer) && cache.size() == capacity) {
                dropRandom();
            }
            MutableInt a = new MutableInt(age);
            if(cache.putIfAbsent(peer, a) == null) {
                ordered.add(new AgedHost(peer, a));
                return true;
            }

        }
        return false;
    }

    public boolean removePeer(Host peer) {
        MutableInt age;
        if((age = cache.remove(peer)) != null) {
            ordered.remove(new AgedHost(peer, age));
            return true;
        }
        return false;
    }

    private void dropRandom() {
        if(cache.size() > 0) {
            int idx = rnd.nextInt(cache.size());
            Host[] hosts = cache.keySet().toArray(new Host[0]);
            Host torm = hosts[idx];
            removePeer(torm);
        }
    }

    public Set<Host> getCache() {
        return cache.keySet();
    }

    public Map<Host, Integer> getRandomAgedSubset(int size) {
        Map<Host, Integer> toret;
        if(cache.size() > size) {
            List<AgedHost> hosts = new ArrayList<>(ordered);
            while (hosts.size() > size)
                hosts.remove(rnd.nextInt(hosts.size()));
            toret = new HashMap<>(hosts.size(), 1);
            hosts.forEach(h -> toret.putIfAbsent(h.h,h.age.getValue()));
        } else {
            toret = new HashMap<>(cache.size(), 1);
            cache.forEach((h,a) -> toret.putIfAbsent(h, a.getValue()));
        }
        return toret;
    }

    public Set<Host> getRandomSubset(int size) {
        Set<Host> toret;
        if(cache.size() > size) {
            List<AgedHost> hosts = new ArrayList<>(ordered);
            while (hosts.size() > size)
                hosts.remove(rnd.nextInt(hosts.size()));
            toret = new HashSet<>(hosts.size(), 1);
            hosts.forEach(h -> toret.add(h.h));
        } else {
            toret = cache.keySet();
        }
        return toret;
    }

    public Map<Host, Integer> getRandomSubsetWith(int size, Host target) {
        Map<Host, Integer> toret;
        if(cache.size() > size) {
            List<AgedHost> hosts = new ArrayList<>(ordered);
            AgedHost host = new AgedHost(target, cache.getOrDefault(target, new MutableInt(0)));
            hosts.remove(host);
            while (hosts.size() > size-1)
                hosts.remove(rnd.nextInt(hosts.size()));
            hosts.add(host);
            toret = new HashMap<>(hosts.size(), 1);
            hosts.forEach(h -> toret.putIfAbsent(h.h,h.age.getValue()));
        } else {
            toret = new HashMap<>(cache.size(), 1);
            cache.forEach((h,a) -> toret.putIfAbsent(h, a.getValue()));
        }
        return toret;
    }

    public Host getRandomFrom(Set<Host> subset) {
        if(subset.size() > 0) {
            int idx = rnd.nextInt(subset.size());
            Host[] hosts = subset.toArray(new Host[0]);
            return hosts[idx];
        } else
            return null;
    }

    public void merge(Map<Host, Integer> subset, Set<Host> sent) {
        Map<Host, Integer> dup = new HashMap<>(subset);
        dup.remove(self);
        dup.keySet().removeAll(cache.keySet());
        Iterator<Host> it = sent.iterator();
        dup.forEach((h, a) -> {
            if(cache.size() == capacity) {
                while(it.hasNext() && !(removePeer(it.next())));
            }
            addPeer(h, a);
        });
    }

    public Host getOldest() {
        if(ordered.size() > 0)
            return ordered.poll().getHost();
        return null;
    }

    public void putBack(Host host) {
        if(cache.containsKey(host)) {
            ordered.add(new AgedHost(host, cache.get(host)));
        }
    }

    public void incAge() {
        cache.forEach((k,v) -> v.increment());
    }

    public void setAge(Host host, int age) {
        MutableInt i = cache.get(host);
        if(i != null)
            i.setValue(age);
    }

    public Queue<AgedHost> getOrdered() {
        return ordered;
    }
}
