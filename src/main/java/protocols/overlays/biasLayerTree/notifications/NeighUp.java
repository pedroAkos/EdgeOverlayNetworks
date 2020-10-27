package protocols.overlays.biasLayerTree.notifications;

import babel.generic.ProtoNotification;
import network.data.Host;

public class NeighUp extends ProtoNotification {
    public static final short NOTIFICATION_ID = 402;
    public static final String NOTIFICATION_NAME = "Neigh Up";

    public enum Status {
        FATHER,
        BROTHER,
        CHILD,
        UNDEFINED,
    }

    private Host neigh;
    private Status status;
    private short layer;

    public NeighUp(Host neigh, short neighLayer, short myLayer) {
        super(NOTIFICATION_ID);
        this.neigh = neigh;
        if(neighLayer > myLayer)
            status = Status.CHILD;
        else if(neighLayer == myLayer)
            status = Status.BROTHER;
        else
            status = Status.FATHER;
        this.layer = neighLayer;
    }

    @Override
    public String toString() {
        return "NeighUp{" +
                (status == Status.FATHER ? "Father" :
                status == Status.BROTHER ? "Brother" :
                status == Status.CHILD ? "Child" : "Undefined") +
                ", host=" + neigh +
                ", layer=" + layer +
                "}";
    }

    public Host getNeigh() {
        return neigh;
    }

    public Status getStatus() {
        return status;
    }

    public short getLayer() {
        return layer;
    }
}
