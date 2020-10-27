package protocols.overlays.cyclon;

import babel.exceptions.HandlerRegistrationException;
import babel.generic.GenericProtocol;
import channel.tcp.TCPChannel;
import channel.tcp.events.*;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.overlays.cyclon.messages.ShuffleMessage;
import protocols.overlays.cyclon.messages.ShuffleReplyMessage;
import protocols.overlays.cyclon.requests.MembershipReply;
import protocols.overlays.cyclon.requests.MembershipRequest;
import protocols.overlays.cyclon.timers.ShuffleTimer;
import protocols.overlays.cyclon.utils.CacheView;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class Cyclon extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Cyclon.class);

    public final static short PROTOCOL_ID = 400;
    public final static String PROTOCOL_NAME = "Cyclon";


    private final int shuffleTime; //param: timeout for shuffle
    private final int subsetSize; //param: maximum size of subset shuffled;

    private final CacheView cacheView;
    private final Host myself;

    private final Map<Host, Set<Host>> ongoing;


    public Cyclon(String channelName, Properties properties, Host myself) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        logger.info("Hello, I am {}", myself);

        int capacity = Integer.parseInt(properties.getProperty("cacheView", "15")); //param: capacity of cacheView
        this.shuffleTime = Short.parseShort(properties.getProperty("shuffleTime", "2000")); //param: timeout for shuffle
        subsetSize = Integer.parseInt(properties.getProperty("shuffleLen", "10"));

        Random rnd = new Random();
        this.myself = myself;
        this.cacheView = new CacheView(capacity, rnd, myself);
        this.ongoing = new HashMap<>();

        int channelId = createChannel(channelName, properties);

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(ShuffleMessage.MSG_ID, ShuffleMessage.serializer);
        registerMessageSerializer(ShuffleReplyMessage.MSG_ID, ShuffleReplyMessage.serializer);

        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, ShuffleMessage.MSG_ID, this::uponShuffle);
        registerMessageHandler(channelId, ShuffleReplyMessage.MSG_ID, this::uponShuffleReply);

        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(ShuffleTimer.TIMER_ID, this::uponShuffleTime);

        /*--------------------- Register Request Handlers ----------------------------- */
        registerRequestHandler(MembershipRequest.REQUEST_ID, this::uponGetPeers);

        /*-------------------- Register Channel Event ------------------------------- */
        registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
        registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
        registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
        registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);
    }


    /*--------------------------------- Messages ---------------------------------------- */
    private void uponShuffle(ShuffleMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        Map<Host, Integer> subset = cacheView.getRandomAgedSubset(msg.getSubset().size());
        sendMessage(new ShuffleReplyMessage(subset), from, TCPChannel.CONNECTION_IN);
        logger.debug("Sent ShuffleReplyMessage to {}", from);
        cacheView.merge(msg.getSubset(), subset.keySet());

    }


    private void uponShuffleReply(ShuffleReplyMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        closeConnection(from);
        Set<Host> subset = ongoing.remove(from);
        cacheView.merge(msg.getSubset(), subset);
        cacheView.putBack(from);
    }


    /*--------------------------------- Timers ---------------------------------------- */
    private void uponShuffleTime(ShuffleTimer timer, long timerId) {
        logger.debug("Shuffle Time: cache{}", cacheView);
        if(cacheView.getOrdered().size() > 0) {
            cacheView.incAge();
            Host target = cacheView.getOldest();
            if(!ongoing.containsKey(target)) {
                Map<Host, Integer> subset = cacheView.getRandomSubsetWith(subsetSize, target);

                subset.remove(target);
                subset.put(myself, 0);

                sendMessage(new ShuffleMessage(subset), target);
                logger.debug("Sent ShuffleMessage to {}", target);
                ongoing.put(target, subset.keySet());
            }
        }
    }


    /*--------------------------------- Requests ---------------------------------------- */
    private void uponGetPeers(MembershipRequest request, short sourceProto) {
        Set<Host> peers;
        if(request.getFanout() <= 0)
            peers = cacheView.getCache();
        else
            peers = cacheView.getRandomSubset(request.getFanout());

        sendReply(new MembershipReply(peers), sourceProto);
    }



    /* --------------------------------- Channel Events ---------------------------- */

    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        logger.trace("Connection to {} is down cause {}", event.getNode(), event.getCause());
    }

    private void uponOutConnectionFailed(OutConnectionFailed event, int channelId) {
        logger.trace("Connection to {} failed cause: {}", event.getNode(), event.getCause());
        cacheView.removePeer(event.getNode());
    }

    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        logger.trace("Connection to {} is up", event.getNode());
    }

    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        logger.trace("Connection from {} is up", event.getNode());
    }

    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        logger.trace("Connection from {} is down, cause: {}", event.getNode(), event.getCause());
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        if (props.containsKey("contacts")) {
            try {
                String[] allContacts = props.getProperty("contacts").split(",");

                for(String contact : allContacts) {
                    String[] hostElems = contact.split(":");
                    cacheView.addPeer(new Host(InetAddress.getByName(hostElems[0]), Short.parseShort(hostElems[1])), 0);
                }

            } catch (Exception e) {
                System.err.println("Invalid contact on configuration: '" + props.getProperty("contacts"));
                e.printStackTrace();
                System.exit(-1);
            }
        }
        setupPeriodicTimer(new ShuffleTimer(), this.shuffleTime, this.shuffleTime);
    }
}
