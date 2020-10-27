package protocols.overlays.biasLayerTree.notifications;


import babel.generic.ProtoNotification;
import network.data.Host;

public class BrotherNotification extends ProtoNotification {

    public static final short NOTIFICATION_ID = 404;
    public static final String NOTIFICATION_NAME = "Brother Change";

    private Host oldBrother;
    private Host newBrother;

    public BrotherNotification(Host oldBrother, Host newBrother) {
        super(NOTIFICATION_ID);
        this.newBrother = newBrother;
        this.oldBrother = oldBrother;
    }

    public Host getNewBrother() {
        return newBrother;
    }

    public Host getOldBrother() {
        return oldBrother;
    }
}
