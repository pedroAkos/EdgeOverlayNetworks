package protocols.overlays.biasLayerTree.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import protocols.overlays.biasLayerTree.utils.LayeredView;
import protocols.overlays.biasLayerTree.utils.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A message meant to perform a random walk within a layer to perform stronger connections in and between layers
 */
public class ForwardJoinMessage extends ProtoMessage {

    public static final short MSG_CODE = 403;

    private short ttl;
    private short maxLayers;
    private LayeredView layeredNodes;
    private List<Host> path; //nodes in the same layer

    private Node newNode;

    public ForwardJoinMessage(Node newNode, short ttl, int capacity, short maxLayers) {
        this(newNode, ttl, new LayeredView(capacity, newNode.getLayer()), new ArrayList<>(ttl), maxLayers);
    }

    private ForwardJoinMessage(Node newNode, short ttl, LayeredView layeredNodes, List<Host> path, short maxLayers) {
        super(MSG_CODE);
        this.newNode = newNode;
        this.ttl = ttl;
        this.layeredNodes = layeredNodes;
        this.layeredNodes.addNode(newNode);
        this.path = path;
        this.maxLayers = maxLayers;
    }

    @Override
    public String toString() {
        return "ForwardJoinMessage{" +
                "ttl=" + ttl +
                ", newNode=" + newNode +
                ", maxLayers=" + maxLayers +
                ", nodes=" + layeredNodes +
                ", path=" +path +
                '}';
    }

    public Node getNewNode() {
        return newNode;
    }

    public short getMaxLayers() {
        return maxLayers;
    }

    public LayeredView getLayeredNodes() {
        return layeredNodes;
    }

    public List<Host> getPath() {
        return path;
    }

    public boolean isInPath(Host h) {
        return path.contains(h);
    }

    public short decrementTtl(Host self) {
        path.add(self);
        return --ttl; //decrement before returning
    }

    public void reset(short ttl, short nextLayer) {
        this.ttl = ttl;
        path = new ArrayList<>();
        layeredNodes.changeBaseLayer(nextLayer);
    }

    public void addNeigh(short layer, Host host, int age, long timestamp) {
        addNeigh(new Node(host, layer, age, timestamp));
    }

    public void addNeigh(Node node) {
        layeredNodes.addOrUpdateNode(node);
    }

    public static final ISerializer<ForwardJoinMessage> serializer = new ISerializer<ForwardJoinMessage>() {
        @Override
        public void serialize(ForwardJoinMessage m, ByteBuf out) throws IOException {

            out.writeShort(m.ttl);

            out.writeShort(m.newNode.getLayer());
            Host.serializer.serialize(m.newNode.getAddress(), out);
            out.writeLong(m.newNode.getTimestamp());

            out.writeShort(m.path.size());
            for(Host h : m.path)
                Host.serializer.serialize(h, out);

            out.writeShort(m.maxLayers);
            while(m.layeredNodes.howManyLayers() > m.maxLayers)
                m.layeredNodes.removeTopLayer();

            out.writeShort(m.layeredNodes.getBaseLayer());
            out.writeShort(m.layeredNodes.howManyLayers());
            out.writeShort(m.layeredNodes.getCapacity());

            for(short l : m.layeredNodes.getSortedLayers()) {
                out.writeShort(l);
                PriorityQueue<Node> nodesInLayer = m.layeredNodes.sortedLayer(Node.distanceComparator(m.newNode.getAddress()), l);
                int size = m.getLayeredNodes().getCapacity() < nodesInLayer.size() ? m.getLayeredNodes().getCapacity() : nodesInLayer.size();
                out.writeShort(size);
                for(int i = 0; i < size; i++) {
                    Node n = nodesInLayer.poll();
                    Host.serializer.serialize(n.getAddress(), out);
                    out.writeInt(n.getAge());
                    out.writeLong(n.getTimestamp());
                }
            }
        }

        @Override
        public ForwardJoinMessage deserialize(ByteBuf in) throws IOException {
            short ttl = in.readShort();
            short hostLayer = in.readShort();
            Host newHost = Host.serializer.deserialize(in);
            long hostTimestamp = in.readLong();


            short size = in.readShort();
            List<Host> path = new ArrayList<>();
            for(int i = 0; i < size; i++) {
                Host h = Host.serializer.deserialize(in);
                path.add(h);
            }

            short maxLayers = in.readShort();

            short baseLayer = in.readShort();
            short nLayers = in.readShort();
            short capacity = in.readShort();

            LayeredView view = new LayeredView(capacity, baseLayer);

            for(int i = 0; i < nLayers; i ++) {
                short layer = in.readShort();
                size = in.readShort();
                for(int j = 0; j < size; j ++) {
                    Host h = Host.serializer.deserialize(in);
                    int age = in.readInt();
                    long timestamp = in.readLong();
                    view.addNode(new Node(h, layer, age, timestamp));
                }
            }


            return new ForwardJoinMessage(new Node(newHost, hostLayer, 0, hostTimestamp), ttl, view, path, maxLayers);
        }

    };
}
