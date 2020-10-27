package protocols.overlays.tman.utils;

import network.data.Host;
import protocols.overlays.tman.utils.profile.Profile;

import java.util.*;

public class View {

    private Map<Host, Node> backing;
    protected Queue<Node> ordered;

    protected Node self;

    public View(Node self) {
        ordered = new PriorityQueue<>(Comparator.comparing(Node::getProfile));
        backing = new HashMap<>();
        this.self =self;
    }

    public View(Collection<Node> nodes, Node self) {
        this(self);
        nodes.forEach(this::addNode);
    }

    @Override
    public String toString() {
        return "View{" + ordered +
                '}';
    }

    public static View fromHost(Set<Host> rndView, Profile profile, Node self) {
        View view = new View(self);
        rndView.forEach(host -> view.addNode(new Node(host, profile)));
        return view;
    }

    public void addNode(Node node) {
        if(backing.putIfAbsent(node.getHost(), node) == null) {
            ordered.add(node);
        }
    }

    public void addOrReplaceNode(Node node) {
        Node old = backing.get(node.getHost());
        if(old != null && old.getProfile().compareTo(node.getProfile()) > 0) {
            backing.put(node.getHost(), node);
            ordered.remove(old); ordered.add(node);
        } else if (old == null)
            addNode(node);
    }


    public View merge(View other) {
        View toret = new View(self);
        toret.ordered.addAll(ordered);
        toret.backing.putAll(backing);
        other.ordered.forEach(toret::addOrReplaceNode);
        return toret;
    }

    public Queue<Node> getNodes() {
        return ordered;
    }

    public Host getFirst() {
        Node n = ordered.peek();
        return n != null ? n.getHost() : null;
    }

    public View selectView(View buffer, int size) {
        View newView = new View(self);
        for (int i = 0; i < size && !buffer.ordered.isEmpty(); i ++) {
            Node node = buffer.ordered.poll();
            if(!node.equals(self))
                newView.addNode(node);
        }
        return newView;
    }

    public Set<Host> getHosts() {
        return backing.keySet();
    }

    public boolean isEmpty() {
        return ordered.isEmpty();
    }
}
