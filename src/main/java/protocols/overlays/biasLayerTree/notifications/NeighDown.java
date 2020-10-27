package protocols.overlays.biasLayerTree.notifications;

import babel.generic.ProtoNotification;
import network.data.Host;

public class NeighDown extends ProtoNotification {
    public static final short NOTIFICATION_ID = 403;
    public static final String NOTIFICATION_NAME = "Neigh Down";


    private Host neigh;
    private NeighUp.Status status;

    public NeighDown(Host neigh, short neighLayer, short myLayer) {
        super(NOTIFICATION_ID);
        this.neigh = neigh;
        if(neighLayer > myLayer)
            status = NeighUp.Status.CHILD;
        else if(neighLayer == myLayer)
            status = NeighUp.Status.BROTHER;
        else if(neighLayer != -1)
            status = NeighUp.Status.FATHER;
        else
            status = NeighUp.Status.UNDEFINED;
    }

    @Override
    public String toString() {
        return "NeighDown{" +
                (status == NeighUp.Status.FATHER ? "Father" :
                status == NeighUp.Status.BROTHER ? "Brother" :
                status == NeighUp.Status.CHILD ? "Child" : "Undefined") +
                ", host=" + neigh +
                "}";
    }

    public Host getNeigh() {
        return neigh;
    }

    public NeighUp.Status getStatus() {
        return status;
    }
}
