package protocols.overlays.tmanWithCyclon;

import babel.exceptions.HandlerRegistrationException;
import babel.generic.GenericProtocol;
import channel.tcp.TCPChannel;
import channel.tcp.events.*;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.overlays.cyclon.requests.MembershipReply;
import protocols.overlays.cyclon.requests.MembershipRequest;
import protocols.overlays.cyclon.timers.ShuffleTimer;
import protocols.overlays.tman.timers.GossipTimer;
import protocols.overlays.tman.utils.Node;
import protocols.overlays.tman.utils.View;
import protocols.overlays.tman.utils.profile.IPAddrProfile;
import protocols.overlays.tman.utils.profile.LayerIPAddrProfile;
import protocols.overlays.tman.utils.profile.Profile;
import protocols.overlays.tmanWithCyclon.messages.GossipReplyMessage;
import protocols.overlays.tmanWithCyclon.messages.GossipMessage;
import protocols.overlays.tmanWithCyclon.messages.ShuffleMessage;
import protocols.overlays.tmanWithCyclon.messages.ShuffleReplyMessage;
import protocols.overlays.tmanWithCyclon.utils.LayeredView;
import protocols.overlays.tmanWithCyclon.utils.ProfiledCacheView;
import utils.HostComp;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class TmanWithCyclon extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(TmanWithCyclon.class);

    public final static short PROTOCOL_ID = 430;
    public final static String PROTOCOL_NAME = "Tman";

    private final int fanout;
    private final int viewSize;

    private final Host myself;
    private Profile myProfile;
    private View view;


    private boolean onGoing;


    private final int shuffleTime; //param: timeout for shuffle
    private final int subsetSize; //param: maximum size of subset shuffled;

    private final ProfiledCacheView cacheView;

    private final Map<Host, Set<Host>> ongoing;



    public TmanWithCyclon(String channelName, Properties properties, Host myself) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        int channelId = createChannel(channelName, properties);


        logger.info("Hello, I am {}", myself);

        int capacity = Integer.parseInt(properties.getProperty("cacheView", "15")); //param: capacity of cacheView
        this.shuffleTime = Short.parseShort(properties.getProperty("shuffleTime", "2000")); //param: timeout for shuffle
        subsetSize = Integer.parseInt(properties.getProperty("shuffleLen", "10"));

        Random rnd = new Random();
        this.myself = myself;
        this.cacheView = new ProfiledCacheView(capacity, rnd, myself);
        this.ongoing = new HashMap<>();


        fanout = Integer.parseInt(properties.getProperty("fanout", "5"));
        viewSize = Integer.parseInt(properties.getProperty("viewSize", "10"));


        String profileType = properties.getProperty("profile", "ipaddr");
        if(profileType.equals("ipaddr")) {
            HostComp.createHostComp(properties.getProperty("hostcomp"));
            myProfile = new IPAddrProfile(myself, myself);
            Profile.registerSerializer(IPAddrProfile.PROFILE_ID, IPAddrProfile.generateSerializer(myself));
            //short layer = Short.parseShort(properties.getProperty("layer", "-1"));
            //myProfile = new LayerIPAddrProfile(myself, layer, myself, layer);
            //Profile.registerSerializer(LayerIPAddrProfile.PROFILE_ID, LayerIPAddrProfile.generateSerializer(myself, layer));
        }

        view = new View(new Node(myself, myProfile));
        //view = new LayeredView(new Node(myself, myProfile), 1, 4);

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(GossipMessage.MSG_ID, GossipMessage.serializer);
        registerMessageSerializer(GossipReplyMessage.MSG_ID, GossipReplyMessage.serializer);

        registerMessageSerializer(ShuffleMessage.MSG_ID, ShuffleMessage.serializer);
        registerMessageSerializer(ShuffleReplyMessage.MSG_ID, ShuffleReplyMessage.serializer);

        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, GossipMessage.MSG_ID, this::uponGossipMessage);
        registerMessageHandler(channelId, GossipReplyMessage.MSG_ID, this::uponGossipReplyMessage);

        registerMessageHandler(channelId, ShuffleMessage.MSG_ID, this::uponShuffle);
        registerMessageHandler(channelId, ShuffleReplyMessage.MSG_ID, this::uponShuffleReply);

        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(GossipTimer.TIMER_ID, this::uponDoGossip);
        registerTimerHandler(ShuffleTimer.TIMER_ID, this::uponShuffleTime);

        /*--------------------- Register Request Handlers --------------------------- */
        registerRequestHandler(MembershipRequest.REQUEST_ID, this::uponMembershipRequest);



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
        logger.debug("Sent GossipReplyMessage to {}", from);
        buffer = view.merge(new View(msg.getNodes(), new Node(myself, myProfile)));
        view = view.selectView(buffer, viewSize);
        //logger.debug("Selected view {}", view);
    }

    private void uponGossipReplyMessage(GossipReplyMessage msg, Host from, short sourceProto, int channel) {
        logger.debug("Received {} from {}", msg, from);
        View buffer = view.merge(new View(msg.getNodes(), new Node(myself, myProfile)));
        view = view.selectView(buffer, viewSize);
        //logger.debug("Selected view {}", view);
        closeConnection(from);
        onGoing = false;
    }

    private View produceBuffer() {
        View buffer = new View(cacheView.getRandomProfiledSubset(fanout), new Node(myself, myProfile));
        buffer.addNode(new Node(myself, myProfile));
        return buffer.merge(view);
    }

    private void uponShuffle(ShuffleMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        Map<Host, Integer> subset = cacheView.getRandomAgedSubset(msg.getSubset().size());
        Set<Node> profiles = cacheView.getProfiles(subset.keySet());

        sendMessage(new ShuffleReplyMessage(subset, profiles), from, TCPChannel.CONNECTION_IN);
        logger.debug("Sent ShuffleReplyMessage to {}", from);
        cacheView.merge(msg.getSubset(), subset.keySet());
        msg.getProfiles().forEach(cacheView::addProfile);

    }


    private void uponShuffleReply(ShuffleReplyMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        closeConnection(from);
        Set<Host> subset = ongoing.remove(from);
        cacheView.merge(msg.getSubset(), subset);
        msg.getProfiles().forEach(cacheView::addProfile);
        cacheView.putBack(from);
    }


    /*--------------------------------- Timers ---------------------------------------- */
    private void uponDoGossip(GossipTimer timer, long timerId) {
        //logger.debug("Performing Gossip");
        Host target = view.getFirst();
        if(!onGoing && !view.isEmpty() && !ongoing.containsKey(target)) {
            onGoing = true;
            View buffer = produceBuffer();
            sendMessage(new GossipMessage(buffer.getNodes()), target);
            logger.debug("Sent GossipMessage to {}", target);
            //logger.debug("Send {} to {}", buffer, target);
        }
    }


    private void uponShuffleTime(ShuffleTimer timer, long timerId) {
        //logger.debug("Shuffle Time: cache{}", cacheView);
        if(cacheView.getOrdered().size() > 0) {
            cacheView.incAge();
            Host target = cacheView.getOldest();
            if(!ongoing.containsKey(target)) {
                Map<Host, Integer> subset = cacheView.getRandomSubsetWith(subsetSize, target);

                subset.remove(target);
                subset.put(myself, 0);
                Set<Node> profiles = cacheView.getProfiles(subset.keySet());
                profiles.add(new Node(myself, myProfile));

                sendMessage(new ShuffleMessage(subset, profiles), target);
                logger.debug("Sent ShuffleMessage to {}", target);
                ongoing.put(target, subset.keySet());
            }
        }
    }

    /*--------------------------------- Requests ---------------------------------------- */
    private void uponMembershipRequest(MembershipRequest request, short sourceProto) {
        sendReply(new MembershipReply(view.getHosts()), sourceProto);
    }

    /* --------------------------------- Channel Events ---------------------------- */

    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        //logger.debug("Connection to {} is down, cause: {}", event.getNode(), event.getCause());
    }

    private void uponOutConnectionFailed(OutConnectionFailed event, int channelId) {
        //logger.debug("Connection to {} failed, cause: {}", event.getNode(), event.getCause());
    }

    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        //logger.debug("Connection to {} is up", event.getNode());
    }

    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        //logger.debug("Connection from {} is up", event.getNode());
    }

    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        //logger.debug("Connection from {} is down, cause: {}", event.getNode(), event.getCause());
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {

        if (props.containsKey("contacts")) {
            try {
                String[] allContacts = props.getProperty("contacts").split(",");

                for(String contact : allContacts) {
                    String[] hostElems = contact.split(":");
                    Host c = new Host(InetAddress.getByName(hostElems[0]), Short.parseShort(hostElems[1]));
                    view.addNode(new Node
                            (c, myProfile.defaultProfile()));
                    cacheView.addPeer(c, 0);
                    cacheView.addProfile(c, myProfile.defaultProfile());
                }

            } catch (Exception e) {
                System.err.println("Invalid contact on configuration: '" + props.getProperty("contacts"));
                e.printStackTrace();
                System.exit(-1);
            }
        }

        long gossipTime = Long.parseLong(props.getProperty("gossipTime", "2000"));

        setupPeriodicTimer(new GossipTimer(), gossipTime*5, gossipTime);
        setupPeriodicTimer(new ShuffleTimer(), this.shuffleTime, this.shuffleTime);
    }
}
