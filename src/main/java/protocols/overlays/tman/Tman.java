package protocols.overlays.tman;

import babel.exceptions.HandlerRegistrationException;
import babel.generic.GenericProtocol;
import channel.tcp.TCPChannel;
import channel.tcp.events.*;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.overlays.cyclon.Cyclon;
import protocols.overlays.cyclon.requests.MembershipReply;
import protocols.overlays.cyclon.requests.MembershipRequest;
import protocols.overlays.tman.messages.GossipMessage;
import protocols.overlays.tman.messages.GossipReplyMessage;
import protocols.overlays.tman.timers.GetPeersTimer;
import protocols.overlays.tman.timers.GossipTimer;
import protocols.overlays.tman.utils.Node;
import protocols.overlays.tman.utils.View;
import protocols.overlays.tman.utils.profile.LayerIPAddrProfile;
import protocols.overlays.tman.utils.profile.Profile;
import utils.HostComp;

import java.io.IOException;
import java.net.InetAddress
        ;
import java.util.Properties;
import java.util.Set;

public class Tman extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Tman.class);

    public final static short PROTOCOL_ID = 430;
    public final static String PROTOCOL_NAME = "Tman";

    private final int fanout;
    private final int viewSize;

    private final Host myself;
    private Profile myProfile;
    private View view;

    private Set<Host> rndView;
    private boolean onGoing;

    public Tman(String channelName, Properties properties, Host myself) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        int channelId = createChannel(channelName, properties);

        fanout = Integer.parseInt(properties.getProperty("fanout", "5"));
        viewSize = Integer.parseInt(properties.getProperty("viewSize", "10"));



        this.myself = myself;

        String profileType = properties.getProperty("profile", "ipaddr");
        if(profileType.equals("ipaddr")) {
            HostComp.createHostComp(properties.getProperty("hostcomp"));
            //myProfile = new IPAddrProfile(myself, myself);
            //Profile.registerSerializer(IPAddrProfile.PROFILE_ID, IPAddrProfile.generateSerializer(myself));
            short layer = Short.parseShort(properties.getProperty("layer", "-1"));
            myProfile = new LayerIPAddrProfile(myself, layer, myself, layer);
            Profile.registerSerializer(LayerIPAddrProfile.PROFILE_ID, LayerIPAddrProfile.generateSerializer(myself, layer));
        }

        view = new View(new Node(myself, myProfile));

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(GossipMessage.MSG_ID, GossipMessage.serializer);
        registerMessageSerializer(GossipReplyMessage.MSG_ID, GossipReplyMessage.serializer);

        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, GossipMessage.MSG_ID, this::uponGossipMessage);
        registerMessageHandler(channelId, GossipReplyMessage.MSG_ID, this::uponGossipReplyMessage);

        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(GossipTimer.TIMER_ID, this::uponDoGossip);
        registerTimerHandler(GetPeersTimer.TIMER_ID, this::uponAskPeers);

        /*--------------------- Register Request Handlers --------------------------- */
        registerRequestHandler(MembershipRequest.REQUEST_ID, this::uponMembershipRequest);

        /*--------------------- Register Reply Handlers ----------------------------- */
        registerReplyHandler(MembershipReply.REPLY_ID, this::uponGetPeers);


        /*-------------------- Register Channel Event ------------------------------- */
        registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
        registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
        registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
        registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);
    }

    /*--------------------------------- Messages ---------------------------------------- */
    private void uponGossipMessage(GossipMessage msg, Host from, short sourceProto, int channel) {
        logger.debug("Received {} from {}", msg, from);
        View buffer = produceBuffer();
        sendMessage(new GossipReplyMessage(buffer.getNodes()), from, TCPChannel.CONNECTION_IN);
        buffer = view.merge(new View(msg.getNodes(), new Node(myself, myProfile)));
        view = view.selectView(buffer, viewSize);
        logger.debug("Selected view {}", view);
    }

    private void uponGossipReplyMessage(GossipReplyMessage msg, Host from, short sourceProto, int channel) {
        logger.debug("Received {} from {}", msg, from);
        View buffer = view.merge(new View(msg.getNodes(), new Node(myself, myProfile)));
        view = view.selectView(buffer, viewSize);
        logger.debug("Selected view {}", view);
        closeConnection(from);
        onGoing = false;
    }

    private View produceBuffer() {
        View buffer = View.fromHost(rndView, myProfile.defaultProfile(), new Node(myself, myProfile));
        buffer.addNode(new Node(myself, myProfile));
        return buffer.merge(view);
    }

    /*--------------------------------- Timers ---------------------------------------- */
    private void uponDoGossip(GossipTimer timer, long timerId) {
        logger.debug("Performing Gossip");
        if(!onGoing && !view.isEmpty()) {
            onGoing = true;
            Host target = view.getFirst();
            View buffer = produceBuffer();
            sendMessage(new GossipMessage(buffer.getNodes()), target);
            logger.debug("Send {} to {}", buffer, target);
        }
    }

    private void uponAskPeers(GetPeersTimer timer, long timerId) {
        sendRequest(new MembershipRequest(fanout), Cyclon.PROTOCOL_ID);
    }

    /*--------------------------------- Replies ---------------------------------------- */
    private void uponGetPeers(MembershipReply reply, short sourceProto) {
        rndView = reply.getPeers();
    }

    /*--------------------------------- Requests ---------------------------------------- */
    private void uponMembershipRequest(MembershipRequest request, short sourceProto) {
        sendReply(new MembershipReply(view.getHosts()), sourceProto);
    }

    /* --------------------------------- Channel Events ---------------------------- */

    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        logger.debug("Connection to {} is down, cause: {}", event.getNode(), event.getCause());
    }

    private void uponOutConnectionFailed(OutConnectionFailed event, int channelId) {
        logger.debug("Connection to {} failed, cause: {}", event.getNode(), event.getCause());
    }

    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        logger.debug("Connection to {} is up", event.getNode());
    }

    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        logger.debug("Connection from {} is up", event.getNode());
    }

    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        logger.debug("Connection from {} is down, cause: {}", event.getNode(), event.getCause());
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {

        if (props.containsKey("contacts")) {
            try {
                String[] allContacts = props.getProperty("contacts").split(",");

                for(String contact : allContacts) {
                    String[] hostElems = contact.split(":");
                    view.addNode(new Node
                            (new Host(InetAddress.getByName(hostElems[0]), Short.parseShort(hostElems[1])),
                                    myProfile.defaultProfile()));
                }

            } catch (Exception e) {
                System.err.println("Invalid contact on configuration: '" + props.getProperty("contacts"));
                e.printStackTrace();
                System.exit(-1);
            }
        }

        long gossipTime = Long.parseLong(props.getProperty("gossipTime", "2000"));
        long getPeersTime = (long) (gossipTime/2);

        setupPeriodicTimer(new GossipTimer(), gossipTime*5, gossipTime);
        setupPeriodicTimer(new GetPeersTimer(), getPeersTime, getPeersTime);
    }
}
