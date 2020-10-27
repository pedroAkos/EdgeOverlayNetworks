package protocols.overlays.biasLayerTree.utils;

import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;
import java.util.*;

public class LayeredView {

    private NavigableMap<Short, Map<Host, Node>> layeredNodes;

    private int size;
    private int capacity;
    private short baseLayer;


    public LayeredView(int capacity, short baseLayer) {
        layeredNodes = new TreeMap<>();
        this.baseLayer = baseLayer;
        this.capacity = capacity+1;
        this.size = 0;
    }

    @Override
    public String toString() {
        return "LayeredView{" +
                "size="+size+
                ", capacity="+capacity+
                ", baseLayer="+baseLayer+
                ", layeredNodes="+layeredNodes+
                "}";
    }

    public int getCapacity() {
        return capacity;
    }

    public short getBaseLayer() {
        return baseLayer;
    }

    public void changeBaseLayer(short baseLayer) {
        this.baseLayer = baseLayer;
    }

    public int layerNodesSize(short layer) {
        return layer(layer).size();
    }

    public int totalSize() {
        return size;
    }

    public int howManyLayers() {
        return layeredNodes.keySet().size();
    }

    public boolean isFull(short layer) {
        Map<Host, Node> nodes = layer(layer);
        if(nodes != null) return (nodes.size() >= maxCapacityInLayer(layer));
        return false;
    }

    public int maxCapacityInLayer(short layer) {
        int max = capacity;
        int distance = 0;

        if(layer < baseLayer) {
            distance = layeredNodes.navigableKeySet().subSet(layer, baseLayer).size();
        } else if(layer > baseLayer) {
            distance = layeredNodes.navigableKeySet().subSet(baseLayer, layer).size();
        }

        max -= distance;
        return max > 0 ? max : 1;
    }

    public Map<Host, Node> removeTopLayer() {
        return removeLayer(layeredNodes.firstKey());
    }

    public Map<Host, Node> removeBottomLayer() {
        return removeLayer(layeredNodes.lastKey());
    }

    public Map<Host, Node> removeLayer(short layer) {
        return layeredNodes.remove(layer);
    }

    public Set<Short> getLayers() {
        return layeredNodes.keySet();
    }

    public NavigableSet<Short> getSortedLayers() {
        return layeredNodes.navigableKeySet();
    }

    public NavigableSet<Short> getDescendingLayers() {
        return layeredNodes.descendingKeySet();
    }

    public NavigableMap<Short, Map<Host, Node>> getLayeredNodes() {
        return layeredNodes;
    }

    public NavigableSet<Short> getTopLayers() {
        return getSortedLayers().headSet(baseLayer, false);
    }

    public Map<Host, Node> flatView() {
        return flatView(layeredNodes);
    }

    public Map<Host, Node> flatView(NavigableMap<Short, Map<Host, Node>> layeredNodes) {
        Map<Host, Node> flatView = new HashMap<>();
        layeredNodes.forEach((l,s) -> flatView.putAll(s));
        return flatView;
    }

    public Map<Host, Node> layer(short layer) {
        return layeredNodes.getOrDefault(layer, new HashMap<>());
    }

    public PriorityQueue<Node> sortedFlatView(Comparator<Node> comparator) {
        return sortedFlatView(comparator, layeredNodes);
    }

    public PriorityQueue<Node> sortedFlatView(Comparator<Node> comparator, NavigableMap<Short, Map<Host, Node>> layeredNodes) {
        PriorityQueue<Node> sortedFlatView = new PriorityQueue<>(comparator);
        sortedFlatView.addAll(flatView(layeredNodes).values());
        return sortedFlatView;
    }

    public PriorityQueue<Node> sortedLayer(Comparator<Node> comparator, short layer) {
        Map<Host, Node> layerNodes = layer(layer);
            PriorityQueue<Node> sortedLayer = new PriorityQueue<>(layerNodes.size()+1, comparator);
            sortedLayer.addAll(layerNodes.values());
            return sortedLayer;
    }

    public boolean removeNode(Node node) {
        if(layer(node.getLayer()).remove(node.getAddress()) != null) {
            size--;
            if(layer(node.getLayer()).isEmpty())
                layeredNodes.remove(node.getLayer());

            return true;
        }
        return false;
    }

    public Node removeHost(Host host) {
        Node n = null;
        for(Short l : getLayers()) {
            if((n = layer(l).get(host)) != null) {
                removeNode(n);
                break;
            }
        }
        return n;
    }

    public void markDead(Host host) {
        Node n;
        for(Short l : getLayers()) {
            if((n = layer(l).get(host)) != null) {
                n.markDead();
                break;
            }
        }
    }

    public boolean addNode(Node node) {
        if(layeredNodes.computeIfAbsent(node.getLayer(), v -> new HashMap<>())
                .putIfAbsent(node.getAddress(), node) == null) {
            size ++;
            return true;
        }
        return false;
    }

    public boolean addOrUpdateNode(Node node) {
        if(!addNode(node)) {
            Node old = getNode(node);
            return old.update(node);
        }
        return true;
    }

    public boolean updateNode(Node node) {
        Node old = layeredNodes.getOrDefault(node.getLayer(), new HashMap<>()).get(node.getAddress());
        if(old != null) {
            return old.update(node);
        }
        return false;
    }

    public Node getNode(Node node) {
        return layeredNodes.getOrDefault(node.getLayer(), new HashMap<>()).get(node.getAddress());
    }

    public void trim(short maxLayers) {
        if(howManyLayers() > maxLayers) {
            NavigableMap<Short, Map<Host, Node>> lower = layeredNodes.tailMap(baseLayer, false);
            NavigableMap<Short, Map<Host, Node>> upper = layeredNodes.headMap(baseLayer, false);
            while (lower.size() > maxLayers / 2 && howManyLayers() > maxLayers) {
                layeredNodes.remove(lower.lastKey());
            }
            while (upper.size() > maxLayers / 2 && howManyLayers() > maxLayers) {
                layeredNodes.remove(lower.firstKey());
            }
        }
    }

    public boolean contains(Node node) {
        return flatView().containsKey(node.getAddress());
    }

    public boolean contains(Host host) {
        return flatView().containsKey(host);
    }

    public static ISerializer<LayeredView> serializer = new ISerializer<LayeredView>() {
        @Override
        public void serialize(LayeredView layeredView, ByteBuf out) throws IOException {
            out.writeShort(layeredView.baseLayer);
            out.writeShort(layeredView.capacity);

            out.writeShort(layeredView.howManyLayers());
            for(short l : layeredView.getSortedLayers()) {
                out.writeShort(l);
                Map<Host, Node> nodesInLayer = layeredView.layer(l);
                out.writeShort(nodesInLayer.size());
                for(Node n : nodesInLayer.values()) {
                    Host.serializer.serialize(n.getAddress(), out);
                    out.writeShort(n.getAge());
                    out.writeLong(n.getTimestamp());
                }
            }
        }

        @Override
        public LayeredView deserialize(ByteBuf in) throws IOException {

            short baseLayer = in.readShort();
            short capacity = in.readShort();
            LayeredView view = new LayeredView(capacity, baseLayer);

            short nLayers = in.readShort();
            for(int i = 0; i < nLayers; i++) {
                short layer = in.readShort();
                short nNodes = in.readShort();
                for(int j = 0; j < nNodes; j++) {
                    Host address = Host.serializer.deserialize(in);
                    short age = in.readShort();
                    long timestamp = in.readLong();
                    view.addNode(new Node(address, layer, age, timestamp));
                }
            }

            return view;
        }
    };

}
