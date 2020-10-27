package protocols.overlays.xbot;

import babel.exceptions.HandlerRegistrationException;
import channel.tcp.TCPChannel;
import network.data.Host;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.overlays.biasLayerTree.notifications.NeighDown;
import protocols.overlays.biasLayerTree.notifications.NeighUp;
import protocols.overlays.biasLayerTree.timers.OptimizationTimer;
import protocols.overlays.hyparview.HyparView;
import protocols.overlays.hyparview.messages.DisconnectMessage;
import protocols.overlays.hyparview.timers.HelloTimeout;
import protocols.overlays.hyparview.timers.ShuffleTimer;
import protocols.overlays.xbot.messages.*;
import protocols.overlays.xbot.timers.DisconnectWaitTimeout;
import protocols.overlays.xbot.timers.OptimizeTimeout;
import protocols.overlays.xbot.timers.OptimizeTimer;
import protocols.overlays.xbot.utils.Oracle;
import protocols.overlays.xbot.utils.MonitoredView;
import protocols.overlays.xbot.utils.UDPLatencyOracle;
import protocols.tester.DisseminationConsumer;

import java.io.IOException;
import java.util.*;

public class Xbot extends HyparView {

    private static final Logger logger = LogManager.getLogger(Xbot.class);

    private final Oracle oracle;

    public static final String PROTOCOL_NAME = "Xbot";

    private final int PSL;
    private final int UNOPT;

    private final long optimizationTime;

    private boolean optimizing;
    private long timeoutId;
    private Long disconnectTimeoutId;

    public Xbot(String channelName, Properties properties, Host myself) throws IOException, HandlerRegistrationException {
        super(channelName, properties, myself);

        int maxActive = Integer.parseInt(properties.getProperty("ActiveView", "4")); //param: maximum active nodes (degree of random overlay)
        int maxPassive = Integer.parseInt(properties.getProperty("PassiveView", "7")); //param: maximum passive nodes

        PSL = Integer.parseInt(properties.getProperty("PSL", "4"));
        UNOPT = Integer.parseInt(properties.getProperty("UNOPT", "2"));
        optimizationTime = Long.parseLong(properties.getProperty("optimizationTime", "15000"));

        long oracleDelta = Long.parseLong(properties.getProperty("oracleDelta", "2000"));
        oracle = new UDPLatencyOracle(myself, myself.getPort(), oracleDelta);

        super.active = new MonitoredView(maxActive, myself, rnd, oracle, false);
        super.passive = new MonitoredView(maxPassive, myself, rnd, oracle, true);

        super.active.setOther(passive, pending);
        super.passive.setOther(active, pending);

        this.optimizing = false;

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(DisconnectWaitMessage.MSG_ID, DisconnectWaitMessage.serializer);
        registerMessageSerializer(OptimizationMessage.MSG_ID, OptimizationMessage.serializer);
        registerMessageSerializer(OptimizationReplyMessage.MSG_ID, OptimizationReplyMessage.serializer);
        registerMessageSerializer(ReplaceMessage.MSG_ID, ReplaceMessage.serializer);
        registerMessageSerializer(ReplaceReplyMessage.MSG_ID, ReplaceReplyMessage.serializer);
        registerMessageSerializer(SwitchMessage.MSG_ID, SwitchMessage.serializer);
        registerMessageSerializer(SwitchReplyMessage.MSG_ID, SwitchReplyMessage.serializer);


        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, DisconnectWaitMessage.MSG_ID, this::uponDisconnectWait, this::uponDisconnectWaitSent);
        registerMessageHandler(channelId, OptimizationMessage.MSG_ID, this::uponOptimization);
        registerMessageHandler(channelId, OptimizationReplyMessage.MSG_ID, this::uponOptimizationReply);
        registerMessageHandler(channelId, ReplaceMessage.MSG_ID, this::uponReplace);
        registerMessageHandler(channelId, ReplaceReplyMessage.MSG_ID, this::uponReplaceReply, this::uponReplaceReplySent);
        registerMessageHandler(channelId, SwitchMessage.MSG_ID, this::uponSwitch);
        registerMessageHandler(channelId, SwitchReplyMessage.MSG_ID, this::uponSwitchReply);

        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(OptimizeTimer.TIMER_ID, this::uponOptimizationTime);
        registerTimerHandler(OptimizeTimeout.TIMER_ID, this::uponOptimizationTimeout);
        registerTimerHandler(DisconnectWaitTimeout.TIMER_ID, this::uponDisconnectWaitTimeout);

    }

    /*--------------------------------- Messages ---------------------------------------- */
    private void uponDisconnectWait(DisconnectWaitMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        if(active.containsPeer(from)) {
            ((MonitoredView) active).moveToPending(from);
            logger.debug("Removed from {} active{}", from, active);
            triggerNotification(new NeighDown(from, (short) -1, (short)-1));
            passive.addPeer(from);
            logger.debug("Added to {} passive{}", from, passive);
            closeConnection(from);
            if(disconnectTimeoutId == null) {
                disconnectTimeoutId = setupTimer(new DisconnectWaitTimeout(from), optimizationTime*4);
            } else {
                logger.error("ALREADY HAVE A DISCONNECT TIMEOUT SET");
            }
        }
    }

    private void uponDisconnectWaitSent(DisconnectWaitMessage msg, Host to, short destProto, int channelId) {
        closeConnection(to);
    }

    private void uponOptimization(OptimizationMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        if(optimizing) {
            sendMessage(new OptimizationReplyMessage(msg.getOld(), myself, false), from, TCPChannel.CONNECTION_IN);
            logger.debug("Sent OptimizationReplyMessage to {}", from);
        } else {
            optimizing = true;
            timeoutId = setupTimer(new OptimizeTimeout(), optimizationTime*4);
            if (!active.fullWithPending(pending)) {
                if (!active.containsPeer(from)) {
                    pending.remove(from);
                    logger.debug("Removed from {} pending{}", from, pending);
                    ((MonitoredView) passive).removePeerKeepMonitoring(from);
                    logger.debug("Removed from {} passive{}", from, passive);
                    active.addPeer(from);
                    logger.debug("Added to {} active{}", from, active);
                    triggerNotification(new NeighUp(from, (short) -1, (short) -1));
                }
                sendMessage(new OptimizationReplyMessage(msg.getOld(), myself, true), from);
                logger.debug("Sent OptimizationReplyMessage to {}", from);
                optimizing = false;
                cancelTimer(timeoutId);
            } else if(active.isFull()) {
                Host[] orderedActive = orderActive();
                Host toDisconnect = orderedActive[UNOPT];
                sendMessage(new ReplaceMessage(msg.getOld(), from), toDisconnect);
                logger.debug("Sent ReplaceMessage to {}", toDisconnect);
            } else {
                sendMessage(new OptimizationReplyMessage(msg.getOld(), myself, false), from, TCPChannel.CONNECTION_IN);
                logger.debug("Sent OptimizationReplyMessage to {}", from);
                optimizing = false;
            }
        }
    }

    private void uponOptimizationReply(OptimizationReplyMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        if(msg.getAnswer()) {
            if(active.containsPeer(msg.getOld())) {
                if(msg.getToDisconnect().equals(from)) { // d == null
                    sendMessage(new DisconnectMessage(), msg.getOld());
                    logger.debug("Sent DisconnectMessage to {}", msg.getOld());
                } else { // d != null
                    sendMessage(new DisconnectWaitMessage(), msg.getOld());
                    logger.debug("Sent DisconnectWaitMessage to {}", msg.getOld());
                }
                ((MonitoredView)active).removePeerKeepMonitoring(msg.getOld());
                logger.trace("Removed from {} active{}", msg.getOld(), active);
                triggerNotification(new NeighDown(msg.getOld(), (short)-1, (short)-1));
                logger.trace("Added to {} passive{}", msg.getOld(), passive);
                passive.addPeer(msg.getOld());
            }
            if(!active.containsPeer(from)) {
                ((MonitoredView) active).removeFromPending(msg.getOld());
                if(disconnectTimeoutId != null) cancelTimer(disconnectTimeoutId); disconnectTimeoutId = null;
                pending.remove(from);
                logger.trace("Removed from {} pending{}", from, pending);
                ((MonitoredView) passive).removePeerKeepMonitoring(from);
                logger.trace("Removed from {} passive{}", from, passive);
                active.addPeer(from);
                logger.trace("Added to {} active{}", from, active);
                triggerNotification(new NeighUp(from, (short) -1, (short) -1));
            }
        } else if(!active.containsPeer(from)) {
            closeConnection(from);
        }
        optimizing = false;
        cancelTimer(timeoutId);
    }


    private void uponReplace(ReplaceMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        if(!optimizing && !active.containsPeer(msg.getOld()) && isBetter(msg.getOld(), from)) {
            sendMessage(new SwitchMessage(msg.getPeer(), from), msg.getOld());
            logger.debug("Sent SwitchMessage to {}", msg.getOld());
            optimizing = true;
            timeoutId = setupTimer(new OptimizeTimeout(), optimizationTime*4);
        } else {
            sendMessage(new ReplaceReplyMessage(msg.getOld(), msg.getPeer(), false), from);
            logger.debug("Sent ReplaceReplyMessage to {}", from);
        }
    }

    private void uponReplaceReply(ReplaceReplyMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        int mode = TCPChannel.CONNECTION_IN;
        if(msg.getAnswer()) {
            if(active.containsPeer(from)) {
                ((MonitoredView) active).removePeerKeepMonitoring(from);
                logger.trace("Removed from {} active{}", from, active);
                handleDropFromActive(from);
            }
            pending.remove(from); pending.remove(msg.getPeer());
            logger.trace("Removed from {} pending{}", from, pending);
            logger.trace("Removed from {} pending{}", msg.getPeer(), pending);
            if(!active.containsPeer(msg.getPeer())) {
                ((MonitoredView) passive).removePeerKeepMonitoring(msg.getPeer());
                logger.trace("Removed from {} passive{}", msg.getPeer(), passive);
                active.addPeer(msg.getPeer());
                logger.trace("Added to {} active{}", msg.getPeer(), active);
                triggerNotification(new NeighUp(msg.getPeer(), (short) -1, (short) -1));
            }
            mode = TCPChannel.CONNECTION_OUT;
        }
        sendMessage(new OptimizationReplyMessage(msg.getOld(), from, msg.getAnswer()), msg.getPeer(), mode);
        logger.debug("Sent OptimizationReplyMessage to {}", msg.getPeer());
        optimizing = false;
        cancelTimer(timeoutId);
    }

    private void uponReplaceReplySent(ReplaceReplyMessage msg, Host to, short sourceProto, int channelId) {
        if(msg.getAnswer())
            closeConnection(to);
    }

    private void uponSwitch(SwitchMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        boolean answer = false;
        int mode = TCPChannel.CONNECTION_IN;
        if(!optimizing && !active.containsPeer(from) && (active.containsPeer(msg.getPeer()) || ((MonitoredView)active).isInPending(msg.getPeer()))) {
            sendMessage(new DisconnectWaitMessage(), msg.getPeer());
            logger.debug("Sent DisconnectWaitMessage to {}", msg.getPeer());
            ((MonitoredView)active).moveToPending(msg.getPeer());
            logger.trace("Removed from {} active{}", msg.getPeer(), active);
            ((MonitoredView)active).removeFromPending(msg.getPeer());
            if(disconnectTimeoutId != null) cancelTimer(disconnectTimeoutId); disconnectTimeoutId = null;
            triggerNotification(new NeighDown(msg.getPeer(), (short)-1, (short)-1));
            passive.addPeer(msg.getPeer());
            logger.trace("Added to {} passive{}", msg.getPeer(), passive);
            ((MonitoredView) passive).removePeerKeepMonitoring(from);
            logger.trace("Removed from {} passive{}", from, passive);
            pending.remove(from); pending.remove(msg.getPeer());
            logger.trace("Removed from {} pending{}", from, pending);
            logger.trace("Removed from {} pending{}", msg.getPeer(), pending);
            active.addPeer(from);
            logger.trace("Added to {} active{}", from, active);
            triggerNotification(new NeighUp(from, (short)-1, (short)-1));
            answer = true;
            mode = TCPChannel.CONNECTION_OUT;
        }

        sendMessage(new SwitchReplyMessage(msg.getPeer(), msg.getCandidate(), answer), from, mode);
        logger.debug("Sent SwitchReplyMessage to {}", from);
    }

    private void uponSwitchReply(SwitchReplyMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        if(msg.getAnswer()) {
            if(active.containsPeer(msg.getCandidate())) {
                ((MonitoredView) active).removePeerKeepMonitoring(msg.getCandidate());
                logger.trace("Removed from {} active{}", msg.getCandidate(), active);
                passive.addPeer(msg.getCandidate());
                logger.trace("Added to {} passive{}", msg.getCandidate(), passive);
                triggerNotification(new NeighDown(msg.getCandidate(), (short) -1, (short) -1));
            }
            if(!active.containsPeer(from)) {
                ((MonitoredView) passive).removePeerKeepMonitoring(from);
                logger.trace("Removed from {} passive{}", from, passive);
                pending.remove(from);
                pending.remove(msg.getPeer());
                logger.trace("Removed from {} pending{}", msg.getPeer(), pending);
                logger.trace("Removed from {} pending{}", from, pending);
                active.addPeer(from);
                logger.trace("Added to {} active{}", from, active);
                triggerNotification(new NeighUp(from, (short) -1, (short) -1));
            }
        } else {
            closeConnection(from);
        }
        sendMessage(new ReplaceReplyMessage(from, msg.getPeer(), msg.getAnswer()), msg.getCandidate());
        logger.debug("Sent ReplaceReplyMessage to {}", msg.getCandidate());
        optimizing = false;
        cancelTimer(timeoutId);
    }

    /*--------------------------------- Timers ---------------------------------------- */
    private void uponOptimizationTime(OptimizeTimer timer, long timerId) {
        logger.trace("Optimizing... active.isFull={} passive.isEmpty={}", active.isFull(), passive.getPeers().isEmpty());
        if(active.isFull() && !passive.getPeers().isEmpty()) {
            Set<Host> candidates = passive.getRandomSample(PSL);
            Host[] orderedActive = orderActive();
            for(int i = (orderedActive.length -1 - UNOPT); i >= 0; i --) {
                if(optimizing)
                    break;

                Host old = orderedActive[i];
                Iterator<Host> it = candidates.iterator();
                while (it.hasNext()) {
                    Host candidate = it.next();
                    it.remove();
                    logger.trace("Optimizing... old={} candidate={}", old, candidate);
                    if(isBetter(candidate, old)) {
                        sendMessage(new OptimizationMessage(old), candidate);
                        logger.debug("Sent OptimizationMessage to {}", candidate);
                        optimizing = true;
                        timeoutId = setupTimer(new OptimizeTimeout(), optimizationTime*4);
                        break;
                    }
                }
            }
        }
    }

    private boolean isBetter(Host candidate, Host old) {
        return oracle.getCost(candidate) < oracle.getCost(old);
    }

    private Host[] orderActive() {
        Queue<Pair<Host, Integer>> ordered = new PriorityQueue<>(Comparator.comparing(Pair::getRight));
        for(Host h : active.getPeers()) {
            ordered.add(new ImmutablePair<>(h, oracle.getCost(h)));
        }
        logger.trace("orderedActive={}", ordered);
        Host[] array = new Host[ordered.size()];
        int i = 0;
        while(!ordered.isEmpty()) {
            Pair<Host, Integer> first = ordered.poll();
            array[i] = first.getLeft();
            i ++;
        }
        return array;
    }

    private void uponOptimizationTimeout(OptimizeTimeout timeout, long timerId) {
        if(timerId == timeoutId)
            optimizing = false;
    }

    private void uponDisconnectWaitTimeout(DisconnectWaitTimeout timeout, long timeoutId) {
        if(disconnectTimeoutId != null && disconnectTimeoutId == timeoutId)
            ((MonitoredView)active).removeFromPending(timeout.getDisconnected());
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        super.init(props);
        setupPeriodicTimer(new OptimizeTimer(), this.optimizationTime, this.optimizationTime);
    }
}
