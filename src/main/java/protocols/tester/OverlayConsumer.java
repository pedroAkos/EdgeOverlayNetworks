package protocols.tester;

import babel.exceptions.HandlerRegistrationException;
import babel.generic.GenericProtocol;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.overlays.biasLayerTree.notifications.BrotherNotification;
import protocols.overlays.biasLayerTree.notifications.FatherNotification;
import protocols.overlays.biasLayerTree.notifications.NeighDown;
import protocols.overlays.biasLayerTree.notifications.NeighUp;
import protocols.overlays.common.notifcations.NeighbourDown;
import protocols.overlays.common.notifcations.NeighbourUp;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class OverlayConsumer extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(OverlayConsumer.class);


    public static final String PROTO_NAME = "OverlayConsumer";
    public static final short PROTO_ID = 22;


    private final Set<Host> fathers = new HashSet<>();
    private final Set<Host> brothers = new HashSet<>();
    private final Set<Host> childs = new HashSet<>();

    public OverlayConsumer(Properties properties) throws HandlerRegistrationException {
        super(PROTO_NAME, PROTO_ID);

        /*------------------ Register Subscribed Notifications -------------- */
        subscribeNotification(NeighUp.NOTIFICATION_ID, this::uponNeighUp);
        subscribeNotification(NeighDown.NOTIFICATION_ID, this::uponNeighDown);
        subscribeNotification(FatherNotification.NOTIFICATION_ID, this::uponChangeFather);
        subscribeNotification(BrotherNotification.NOTIFICATION_ID, this::uponChangeBrother);
    }

    private void uponChildUp(Host child) {
        if(childs.add(child)) {
            logger.info("New child " + child);
        } else {
            logger.warn("Unexpected child up: {} already in set", child);
        }
    }

    private void uponBrotherUp(Host brother)  {
        if( brothers.add(brother)) {
            logger.info("New brother " + brother);
        } else {
            logger.warn("Unexpected brother up: {} already in set", brother);
        }
    }

    private void uponFatherUp(Host father) {
        if(fathers.add(father)) {
            logger.info("New father " + father);
        } else {
            logger.warn("Unexpected father up: {} already in set", father);
        }
    }

    private void uponNeighUp(NeighUp notification, short emitterProto) {
        triggerNotification(new NeighbourUp(notification.getNeigh()));
        switch (notification.getStatus()){
            case CHILD:
                uponChildUp(notification.getNeigh());
                break;
            case BROTHER:
                uponBrotherUp(notification.getNeigh());
                break;
            case FATHER:
                uponFatherUp(notification.getNeigh());
                break;
            default:
                logger.error("Received unexpected notification " + notification);
        }
    }


    private void uponChildDown(Host childAddress) {
        if(childs.remove(childAddress)) {
            logger.info("Child " + childAddress + " is down");
        } else {
            logger.warn("Unexpected child down: {} not in set", childAddress);
        }
    }

    private void uponBrotherDown(Host brotherAddress) {
        if (brothers.remove(brotherAddress)) {
            logger.info("Brother "+ brotherAddress + " is down");
        } else {
            logger.warn("Unexpected brother down: {} not in set", brotherAddress);
        }

    }

    private void uponFatherDown(Host fatherAddress) {
        if(fathers.remove(fatherAddress)) {
            logger.info("Father "+ fatherAddress + " is down");
        } else {
            logger.warn("Unexpected father down: {} not in set", fatherAddress);
        }
    }

    private void uponNeighDown(NeighDown notification, short emitterProto) {
        triggerNotification(new NeighbourDown(notification.getNeigh()));
        switch (notification.getStatus()){
            case CHILD:
                uponChildDown(notification.getNeigh());
                break;
            case BROTHER:
                uponBrotherDown(notification.getNeigh());
                break;
            case FATHER:
                uponFatherDown(notification.getNeigh());
                break;
            default:
                logger.error("Received unexpected notification " + notification);
        }
    }

    private void uponChangeFather(FatherNotification notification, short emitterProto) {
        triggerNotification(new NeighbourUp(notification.getNewFather()));
        triggerNotification(new NeighbourDown(notification.getOldFather()));
        if(fathers.remove(notification.getOldFather()) && fathers.add(notification.getNewFather())) {
            logger.info("Replaced father " + notification.getOldFather()+"-"+ notification.getOldFatherLayer() +
                    " by " + notification.getNewFather());
        } else
            logger.error("Received unexpected change father " + notification + " current father " + fathers);
    }

    private void uponChangeBrother(BrotherNotification notification, short emitterProto) {
        triggerNotification(new NeighbourUp(notification.getNewBrother()));
        triggerNotification(new NeighbourDown(notification.getOldBrother()));
        if(brothers.remove(notification.getOldBrother()) && brothers.add(notification.getNewBrother())) {
            logger.info("Replaced brother " + notification.getOldBrother() +
                    " by " + notification.getNewBrother());
        } else {
            logger.error("Received unexpected change brother " + notification + " brothers " +brothers );
        }
    }



    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {

    }
}
