package protocols.dissemination.flood;

import babel.exceptions.HandlerRegistrationException;
import babel.generic.GenericProtocol;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.dissemination.plumtree.messages.GossipMessage;
import protocols.dissemination.plumtree.requests.BroadcastRequest;
import protocols.dissemination.plumtree.requests.DeliverReply;
import protocols.dissemination.plumtree.utils.HashProducer;
import protocols.overlays.common.notifcations.NeighbourDown;
import protocols.overlays.common.notifcations.NeighbourUp;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class FloodGossip extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(FloodGossip.class);

    public static final String PROTO_NAME = "FloodGossip";
    public static final short PROTO_ID = 310;


    private final Host myself;
    private final Set<Host> neighbours;
    private final Set<Integer> received;


    private final HashProducer hashProducer;

    public FloodGossip(String channelName, Properties properties, Host myself) throws IOException, HandlerRegistrationException {
        super(PROTO_NAME, PROTO_ID);

        this.hashProducer = new HashProducer(myself);

        this.myself = myself;
        neighbours = new HashSet<>();
        received = new HashSet<>();


        int channelId = createChannel(channelName, properties);

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(GossipMessage.MSG_ID, GossipMessage.serializer);


        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, GossipMessage.MSG_ID, this::uponReceiveGossip);


        /*--------------------- Register Request Handlers ----------------------------- */
        registerRequestHandler(BroadcastRequest.REQUEST_ID, this::uponBroadcast);


        /*--------------------- Register Notification Handlers ----------------------------- */
        subscribeNotification(NeighbourUp.NOTIFICATION_ID, this::uponNeighbourUp);
        subscribeNotification(NeighbourDown.NOTIFICATION_ID, this::uponNeighbourDown);

    }


    /*--------------------------------- Messages ---------------------------------------- */
    private void uponReceiveGossip(GossipMessage msg, Host from, short sourceProto, int channelId) {
        logger.trace("Received {} from {}", msg, from);
        if(received.add(msg.getMid())) {
            sendReply(new DeliverReply(msg.getContent()), msg.getToDeliver());
            neighbours.forEach(host ->
            {
                if(!host.equals(from)) {
                    sendMessage(msg, host);
                    logger.trace("Sent {} to {}", msg, host);
                }
            });
        }
    }


    /*--------------------------------- Requests ---------------------------------------- */
    private void uponBroadcast(BroadcastRequest request, short sourceProto) {
        int mid = hashProducer.hash(request.getMsg());
        GossipMessage msg = new GossipMessage(mid, 0, sourceProto, request.getMsg());
        uponReceiveGossip(msg, myself, PROTO_ID, -1);

    }

    /*--------------------------------- Notifications ---------------------------------------- */
    private void uponNeighbourUp(NeighbourUp notification, short sourceProto) {
        neighbours.add(notification.getNeighbour());
    }

    private void uponNeighbourDown(NeighbourDown notification, short sourceProto) {
        neighbours.remove(notification.getNeighbour());
        closeConnection(notification.getNeighbour());
    }


    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {

    }
}
