package protocols.overlays.common.notifcations;

import babel.generic.ProtoNotification;
import network.data.Host;

public class NeighbourUp extends ProtoNotification {

    public static final short NOTIFICATION_ID = 21;

    private final Host neighbour;

    public NeighbourUp(Host neighbour) {
        super(NOTIFICATION_ID);
        this.neighbour = neighbour;
    }

    public Host getNeighbour() {
        return neighbour;
    }
}
