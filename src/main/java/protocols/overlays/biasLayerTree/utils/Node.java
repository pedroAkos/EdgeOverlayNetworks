package protocols.overlays.biasLayerTree.utils;

import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import utils.HostComp;
import utils.IHostComparator;

import java.io.IOException;
import java.util.Comparator;

public class Node implements Comparable<Node> {

    private Host address;
    private short layer;
    private int age;
    private long timestamp;
    private boolean dead;

    public Node(Host address, short layer, int age, long timestamp) {
        this.address = address;
        this.layer = layer;
        this.age = age;
        this.timestamp = timestamp;
        dead = false;
    }

    public static Comparator<Node> distanceComparator(Host host) {
        return new NodeDistanceComparator(host);
    }

    public static Comparator<Node> invertedDistanceComparator(Host host) {
        return new NodeInvertedDistanceComparator(host);
    }

    public static Comparator<Node> ageComparator() {
        return new NodeAgeComparator();
    }

    public static Comparator<Node> invertedAgeComparator() {
        return new NodeInvertedAgeComparator();
    }

    public static Comparator<Node> ageAndDistanceComparator(Host target) {
        return new NodeAgeAndDistanceComparator(target);
    }

    public static Comparator<Node> invertedAgeAndDistanceComparator(Host target) {
        return new NodeInvertedAgeAndDistanceComparator(target);
    }


    @Override
    public String toString() {
        return "Node{" +
                "address=" + address +
                ", layer="+ layer +
                ", age=" + age +
                ", timestamp="+timestamp+
                "}";
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (!(other instanceof Node)) {
            return false;
        } else {
            return address.equals(((Node)other).getAddress());
        }
    }

    public short getLayer() {
        return layer;
    }

    public Host getAddress() {
        return address;
    }

    public int getAge() {
        return age;
    }

    public void markDead() {
        dead = true;
    }

    public boolean isDead() {
        return dead;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public int compareTo(Node node) {
        return address.compareTo(node.address);
    }

    public int incrementAge() {
        return ++this.age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public boolean update(Node node) {
        if(this.timestamp < node.timestamp) {
            this.timestamp = node.timestamp;
            this.age = node.age;
            this.dead = false;
            return true;
        }
        return false;
    }

    private static class NodeDistanceComparator implements Comparator<Node> {
        IHostComparator hostComparator;
        public NodeDistanceComparator(Host host) {
            hostComparator = HostComp.host_comp.getInstance(host);
        }

        @Override
        public int compare(Node n1, Node n2) {
            if((n1.isDead() && n2.isDead()) || (!n1.isDead() && !n2.isDead()))
                return hostComparator.compare(n1.address, n2.address);
            else if(n1.isDead() && !n2.isDead())
                return 1;
            else
                return -1;
        }
    }

    private static class NodeInvertedDistanceComparator implements Comparator<Node> {
        NodeDistanceComparator comparator;
        public NodeInvertedDistanceComparator(Host host) {
            comparator = new NodeDistanceComparator(host);
        }


        @Override
        public int compare(Node n1, Node n2) {
            return comparator.compare(n2, n1);
        }
    }


    private static class NodeInvertedAgeComparator implements Comparator<Node> {
        NodeAgeComparator comparator;
        public NodeInvertedAgeComparator(){
            this.comparator = new NodeAgeComparator();
        }
        @Override
        public int compare(Node n1, Node n2) {
            return comparator.compare(n2, n1);
        }
    }

    private static class NodeAgeComparator implements Comparator<Node> {
        @Override
        public int compare(Node n1, Node n2) {
            if((n1.isDead() && n2.isDead()) || (!n1.isDead() && !n2.isDead()))
                return n1.age - n2.age;
            else if (n1.isDead() && !n2.isDead())
                return 1;
            else
                return -1;
        }
    }

    private static class NodeInvertedAgeAndDistanceComparator implements Comparator<Node> {
        NodeAgeAndDistanceComparator comparator;
        public NodeInvertedAgeAndDistanceComparator(Host target) {
            this.comparator = new NodeAgeAndDistanceComparator(target);
        }

        @Override
        public int compare(Node n1, Node n2) {
            return comparator.compare(n2, n1);
        }
    }

    private static class NodeAgeAndDistanceComparator implements Comparator<Node> {
        IHostComparator hostComparator;
        public NodeAgeAndDistanceComparator(Host target) {
            hostComparator = HostComp.host_comp.getInstance(target);
        }

        @Override
        public int compare(Node n1, Node n2) {
            int score = n1.age - n2.age;
            if((n1.isDead() && n2.isDead()) || (!n1.isDead() && !n2.isDead()))
                return score == 0 ? hostComparator.compare(n1.address, n2.address) : score;
            else if(n1.isDead() && !n2.isDead())
                return 1;
            else
                return -1;
        }
    }

    public static ISerializer<Node> serializer = new ISerializer<Node>() {
        @Override
        public void serialize(Node node, ByteBuf out) throws IOException {
            out.writeShort(node.layer);
            Host.serializer.serialize(node.address, out);
            out.writeShort(node.age);
            out.writeLong(node.timestamp);
        }

        @Override
        public Node deserialize(ByteBuf in) throws IOException {
            short layer = in.readShort();
            Host address = Host.serializer.deserialize(in);
            short age = in.readShort();
            long timestamp = in.readLong();
            return new Node(address, layer, age, timestamp);
        }
    };

}
