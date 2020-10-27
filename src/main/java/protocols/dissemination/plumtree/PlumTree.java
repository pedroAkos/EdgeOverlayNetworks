package protocols.dissemination.plumtree;

import babel.events.InternalEvent;
import babel.exceptions.HandlerRegistrationException;
import babel.generic.GenericProtocol;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.dissemination.plumtree.messages.GossipMessage;
import protocols.dissemination.plumtree.messages.GraftMessage;
import protocols.dissemination.plumtree.messages.IHaveMessage;
import protocols.dissemination.plumtree.messages.PruneMessage;
import protocols.dissemination.plumtree.requests.BroadcastRequest;
import protocols.dissemination.plumtree.requests.DeliverReply;
import protocols.dissemination.plumtree.timers.IHaveTimeout;
import protocols.dissemination.plumtree.utils.AddressedIHaveMessage;
import protocols.dissemination.plumtree.utils.HashProducer;
import protocols.dissemination.plumtree.utils.LazyQueuePolicy;
import protocols.dissemination.plumtree.utils.MessageSource;
import protocols.overlays.common.notifcations.NeighbourDown;
import protocols.overlays.common.notifcations.NeighbourUp;

import java.io.IOException;
import java.util.*;

public class PlumTree extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(PlumTree.class);

    public final static short PROTOCOL_ID = 300;
    public final static String PROTOCOL_NAME = "PlumTree";

    private final int space;

    private final long timeout1;
    private final long timeout2;

    private final Host myself;

    private final Set<Host> eager;
    private final Set<Host> lazy;

    private final Map<Integer, Queue<MessageSource>> missing;
    private final Map<Integer, GossipMessage> received;

    private final Queue<Integer> stored;

    private final Map<Integer, Long> onGoingTimers;
    private final Queue<AddressedIHaveMessage> lazyQueue;

    private final LazyQueuePolicy policy;

    private final HashProducer hashProducer;

    public PlumTree(String channelName, Properties properties, Host myself) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.myself = myself;
        this.hashProducer = new HashProducer(myself);

        this.space = Integer.parseInt(properties.getProperty("space", "6000"));
        this.stored = new LinkedList<>();

        this.eager = new HashSet<>();
        this.lazy = new HashSet<>();

        this.missing = new HashMap<>();
        this.received = new HashMap<>();
        this.onGoingTimers = new HashMap<>();
        this.lazyQueue = new LinkedList<>();

        this.policy = HashSet::new;

        this.timeout1 = Long.parseLong(properties.getProperty("timeout1", "1000"));
        this.timeout2 = Long.parseLong(properties.getProperty("timeout2", "500"));

        int channelId = createChannel(channelName, properties);
        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(GossipMessage.MSG_ID, GossipMessage.serializer);
        registerMessageSerializer(PruneMessage.MSG_ID, PruneMessage.serializer);
        registerMessageSerializer(GraftMessage.MSG_ID, GraftMessage.serializer);
        registerMessageSerializer(IHaveMessage.MSG_ID, IHaveMessage.serializer);


        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, GossipMessage.MSG_ID, this::uponReceiveGossip);
        registerMessageHandler(channelId, PruneMessage.MSG_ID, this::uponReceivePrune);
        registerMessageHandler(channelId, GraftMessage.MSG_ID, this::uponReceiveGraft);
        registerMessageHandler(channelId, IHaveMessage.MSG_ID, this::uponReceiveIHave);


        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(IHaveTimeout.TIMER_ID, this::uponIHaveTimeout);


        /*--------------------- Register Request Handlers ----------------------------- */
        registerRequestHandler(BroadcastRequest.REQUEST_ID, this::uponBroadcast);


        /*--------------------- Register Notification Handlers ----------------------------- */
        subscribeNotification(NeighbourUp.NOTIFICATION_ID, this::uponNeighbourUp);
        subscribeNotification(NeighbourDown.NOTIFICATION_ID, this::uponNeighbourDown);
    }


    /*--------------------------------- Messages ---------------------------------------- */
    private void uponReceiveGossip(GossipMessage msg, Host from, short sourceProto, int channelId) {
        logger.trace("Received {} from {}", msg, from);
        if(!received.containsKey(msg.getMid())) {
            sendReply(new DeliverReply(msg.getContent()), msg.getToDeliver());
            received.put(msg.getMid(), msg);
            stored.add(msg.getMid());
            if (stored.size() > space) {
                int torm = stored.poll();
                received.put(torm, null);
            }

            Long tid;
            if((tid = onGoingTimers.remove(msg.getMid())) != null) {
                cancelTimer(tid);
            }
            eagerPush(msg, msg.getRound() +1, from);
            lazyPush(msg, msg.getRound() +1, from);

            if(eager.add(from)) {
                logger.trace("Added {} to eager {}", from, eager);
                logger.debug("Added {} to eager", from);
            }
            if(lazy.remove(from)) {
                logger.trace("Removed {} from lazy {}", from, lazy);
                logger.debug("Removed {} from lazy", from);
            }

            optimize(msg, msg.getRound(), from);
        } else {
            if(eager.remove(from)) {
                logger.trace("Removed {} from eager {}", from, eager);
                logger.debug("Removed {} from eager", from);
            } if(lazy.add(from)) {
                logger.trace("Added {} to lazy {}", from, lazy);
                logger.debug("Added {} to lazy", from);
            }
            sendMessage(new PruneMessage(), from);
        }
    }

    private void eagerPush(GossipMessage msg, int round, Host from) {
        for(Host peer : eager) {
            if(!peer.equals(from)) {
                sendMessage(msg.setRound(round), peer);
                logger.trace("Sent {} to {}", msg, peer);
            }
        }
    }

    private void lazyPush(GossipMessage msg, int round, Host from) {
        for(Host peer : lazy) {
            if(!peer.equals(from)) {
                lazyQueue.add(new AddressedIHaveMessage(new IHaveMessage(msg.getMid(), round), peer));
            }
        }
        dispatch();
    }

    private void dispatch() {
        Set<AddressedIHaveMessage> announcements = policy.apply(lazyQueue);
        for(AddressedIHaveMessage msg : announcements) {
            sendMessage(msg.msg, msg.to);
        }
        lazyQueue.removeAll(announcements);
    }


    private void optimize(GossipMessage msg, int round, Host from) {

    }

    private void uponReceivePrune(PruneMessage msg, Host from, short sourceProto, int channelId) {
        logger.trace("Received {} from {}", msg, from);
        if(eager.remove(from)) {
            logger.trace("Removed {} from eager {}", from, eager);
            logger.debug("Removed {} from eager", from);
        }
        if(lazy.add(from)) {
            logger.trace("Added {} to lazy {}", from, lazy);
            logger.debug("Added {} to lazy", from);
        }
    }

    private void uponReceiveGraft(GraftMessage msg, Host from, short sourceProto, int channelId) {
        logger.trace("Received {} from {}", msg, from);
        if(eager.add(from)) {
            logger.trace("Added {} to eager {}", from, eager);
            logger.debug("Added {} to eager", from);
        } if(lazy.remove(from)) {
            logger.trace("Removed {} from lazy {}", from, lazy);
            logger.debug("Removed {} from lazy", from);
        }

        if(received.getOrDefault(msg.getMid(), null) != null) {
            sendMessage(received.get(msg.getMid()), from);
        }
    }

    private void uponReceiveIHave(IHaveMessage msg, Host from, short sourceProto, int channelId) {
        logger.trace("Received {} from {}", msg, from);
        if(!received.containsKey(msg.getMid())) {
            if(!onGoingTimers.containsKey(msg.getMid())) {
                long tid = setupTimer(new IHaveTimeout(msg.getMid()), timeout1);
                onGoingTimers.put(msg.getMid(), tid);
            }
            missing.computeIfAbsent(msg.getMid(), v-> new LinkedList<>()).add(new MessageSource(from, msg.getRound()));
        }
    }

    /*--------------------------------- Timers ---------------------------------------- */
    private void uponIHaveTimeout(IHaveTimeout timeout, long timerId) {
        if(!received.containsKey(timeout.getMid())) {
            MessageSource msgSrc = missing.get(timeout.getMid()).poll();
            if (msgSrc != null) {
                long tid = setupTimer(timeout, timeout2);
                onGoingTimers.put(timeout.getMid(), tid);

                if (eager.add(msgSrc.peer)) {
                    logger.trace("Added {} to eager {}", msgSrc.peer, eager);
                    logger.debug("Added {} to eager", msgSrc.peer);
                }
                if (lazy.remove(msgSrc.peer)) {
                    logger.trace("Removed {} from lazy {}", msgSrc.peer, lazy);
                    logger.debug("Removed {} from lazy", msgSrc.peer);
                }

                sendMessage(new GraftMessage(timeout.getMid(), msgSrc.round), msgSrc.peer);
            }
        }
    }


    /*--------------------------------- Requests ---------------------------------------- */
    private void uponBroadcast(BroadcastRequest request, short sourceProto) {
        int mid = hashProducer.hash(request.getMsg());
        GossipMessage msg = new GossipMessage(mid, 0, sourceProto, request.getMsg());
        eagerPush(msg, 0, myself);
        lazyPush(msg, 0, myself);
        sendReply(new DeliverReply(request.getMsg()), sourceProto);
        received.put(mid, msg);
        stored.add(mid);
        if (stored.size() > space) {
            int torm = stored.poll();
            received.put(torm, null);
        }
    }

    /*--------------------------------- Notifications ---------------------------------------- */
    private void uponNeighbourUp(NeighbourUp notification, short sourceProto) {
        if(eager.add(notification.getNeighbour())) {
            logger.trace("Added {} to eager {}", notification.getNeighbour(), eager);
            logger.debug("Added {} to eager", notification.getNeighbour());
        } else
            logger.trace("Received neigh up but {} is already in eager {}", notification.getNeighbour(), eager);
    }

    private void uponNeighbourDown(NeighbourDown notification, short sourceProto) {
        if(eager.remove(notification.getNeighbour())) {
            logger.trace("Removed {} from eager {}", notification.getNeighbour(), eager);
            logger.debug("Removed {} from eager", notification.getNeighbour());
        }
        if(lazy.remove(notification.getNeighbour())) {
            logger.trace("Removed {} from lazy {}", notification.getNeighbour(), lazy);
            logger.debug("Removed {} from lazy", notification.getNeighbour());
        }

        MessageSource msgSrc  = new MessageSource(notification.getNeighbour(), 0);
        for(Queue<MessageSource> iHaves : missing.values()) {
            iHaves.remove(msgSrc);
        }
        closeConnection(notification.getNeighbour());
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {

    }
}
