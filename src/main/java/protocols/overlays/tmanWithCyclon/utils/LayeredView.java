package protocols.overlays.tmanWithCyclon.utils;

import network.data.Host;
import protocols.overlays.tman.utils.Node;
import protocols.overlays.tman.utils.View;
import protocols.overlays.tman.utils.profile.LayerIPAddrProfile;

public class LayeredView extends View {

    private int topNodes;
    private int middleNodes;

    public LayeredView(Node target, int topNodes, int middleNodes) {
        super(target);
        this.topNodes = topNodes;
        this.middleNodes = middleNodes;
    }

    @Override
    public Host getFirst() {
        return super.getFirst();
    }

    @Override
    public View merge(View other) {
        View toret = new LayeredView(self, topNodes, middleNodes);
        ordered.forEach(toret::addNode);
        other.getNodes().forEach(toret::addOrReplaceNode);
        return toret;
    }

    @Override
    public View selectView(View buffer, int size) {
        View newView = new LayeredView(self, topNodes, middleNodes);

        int TopNode = 0;
        int MiddleNode = 0;

        int mylayer = ((LayerIPAddrProfile)self.getProfile()).getLayer();

        while (newView.getNodes().size() < size && !buffer.isEmpty()) {
            Node node = buffer.getNodes().poll();
            if(!node.equals(self)) {
                int layer = ((LayerIPAddrProfile)node.getProfile()).getLayer();
                if(layer < mylayer){
                    if(TopNode < topNodes) {
                        newView.addNode(node);
                        TopNode++;
                    }
                } else if (layer == mylayer) {
                    if(MiddleNode < middleNodes) {
                        newView.addNode(node);
                        MiddleNode++;
                    }
                } else
                    newView.addNode(node);
            }

        }
        return newView;
    }
}
