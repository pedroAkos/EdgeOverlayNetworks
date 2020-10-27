package protocols.overlays.biasLayerTree;


import babel.exceptions.HandlerRegistrationException;
import babel.generic.GenericProtocol;
import channel.tcp.TCPChannel;
import channel.tcp.events.*;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.overlays.biasLayerTree.messages.*;
import protocols.overlays.biasLayerTree.notifications.*;
import protocols.overlays.biasLayerTree.timers.*;
import protocols.overlays.biasLayerTree.utils.LayeredView;
import protocols.overlays.biasLayerTree.utils.Node;
import utils.HostComp;
import utils.IHostComparator;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

import static utils.IHostComparator.HostComparator.ipToInt;

public class BiasLayeredTree extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(BiasLayeredTree.class);

    public final static short PROTOCOL_ID = 400;
    public final static String PROTOCOL_NAME = "BiasLayeredTree";


    //config params
    private short layer; //a value from 0 to infinity ?
    //0 = network center
    // l > 0 outer layers;
    // l' > l then l' is a layer further than l
    private short layerRW; // a value for a biased random walk to be performed in the same level

    private short maxLayers; //max layers to carry in forward join
    private short maxNeighs; //max neighbors per layer to carry in forward join
    //private short maxAccept; //max neighbors to add per joinReply;

    private short k_p; //max sample from knownNeighs (passive)
    private short k_a; //max sample from layerNeighs (active)
    //private short maxAge; //lifetime of knonwHosts entry

    //private int layerWeight; //the weight for scoring for a layer

    private short n_neighs; //the number of neighbours I know from my layer
    private short b_neighs; //the number of neighbours I know for backup in my layer
    //b_neighs > n_neighs -> analogous to active view & passive view

    private int timeout; //the timeout for any given message.
    private int shuffleTime; //periodicity of findBrothers;
    private int optimizeTime; //periodicity to verify optimizations
    private int longDistanceShuffleTime; //periodicity of long distance shuffle

    //state
    private IHostComparator host_comp;

    /* -------------------------------- STATE --------------------------- */
    private Host myself;
    private final Random rand;
    private long timestamp;
    private LayeredView activeView; //active view hosts all outgoing connections
    private LayeredView controlView; //control view hosts all incoming connections
    private LayeredView passiveView; //passive view hosts all knonwn hosts to which I don't have an outgoing connection


    private Node optimizedLayer;
    private Node optimizedTop;


    public BiasLayeredTree(String channelName, Properties properties, Host myself) throws HandlerRegistrationException, IOException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        HostComp.createHostComp(properties.getProperty("hostcomp"));

        optimizedLayer = null;
        optimizedTop = null;

        int channelId = createChannel(channelName, properties);
        this.myself = myself;
        rand = new Random(ipToInt(myself.getAddress()) + myself.getPort());

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(JoinMessage.MSG_CODE, JoinMessage.serializer);
        registerMessageSerializer(ForwardJoinMessage.MSG_CODE, ForwardJoinMessage.serializer);
        registerMessageSerializer(HelloNeighborMessage.MSG_CODE, HelloNeighborMessage.serializer);
        registerMessageSerializer(HelloNeighborReplyMessage.MSG_CODE, HelloNeighborReplyMessage.serializer);
        registerMessageSerializer(FatherMessage.MSG_CODE, FatherMessage.serializer);
        registerMessageSerializer(DisconnectMessage.MSG_CODE, DisconnectMessage.serializer);
        registerMessageSerializer(ShuffleMessage.MSG_CODE, ShuffleMessage.serializer);
        registerMessageSerializer(ShuffleReplyMessage.MSG_CODE, ShuffleReplyMessage.serializer);

        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, JoinMessage.MSG_CODE, this::uponReceiveJoin);
        registerMessageHandler(channelId, ForwardJoinMessage.MSG_CODE, this::uponReceiveForwardJoin);
        registerMessageHandler(channelId, HelloNeighborMessage.MSG_CODE, this::uponReceiveHello);
        registerMessageHandler(channelId, HelloNeighborReplyMessage.MSG_CODE, this::uponReceiveHelloReply);
        registerMessageHandler(channelId, FatherMessage.MSG_CODE, this::uponReceiveFather);
        registerMessageHandler(channelId, DisconnectMessage.MSG_CODE, this::uponReceiveDisconnect);
        registerMessageHandler(channelId, ShuffleMessage.MSG_CODE, this::uponReceiveShuffle);
        registerMessageHandler(channelId, ShuffleReplyMessage.MSG_CODE, this::uponReceiveShuffleReply);

        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(JoinTimeout.TIMER_CODE, this::uponTimeout);
        registerTimerHandler(NeighShuffle.TIMER_CODE, this::uponNeighShuffle);
        registerTimerHandler(OptimizationTimer.TIMER_CODE, this::uponOptimization);
        registerTimerHandler(LongDistanceShuffle.TIMER_CODE, this::uponLongDistanceShuffle);
        registerTimerHandler(FillViewTimer.TIMER_CODE, this::uponFillView);


        /*-------------------- Register Channel Event ------------------------------- */
        registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
        registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
        registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
        registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);

    }

    private long incTimeStamp() {
        timestamp = System.currentTimeMillis();
        return timestamp;
    }

    private Node newSelf() {
        return new Node(myself, this.layer, 0, incTimeStamp());
    }

    private void addNodeToActive(Node newNode) {
        passiveView.removeNode(newNode);
        if(activeView.addNode(newNode)) {
            triggerNotification(new NeighUp(newNode.getAddress(), newNode.getLayer(), this.layer));
            logger.info("Node up: " + newNode);
        }
    }

    private void addNodeToActiveAndControl(Node newNode){
        addNodeToActive(newNode);
        controlView.addNode(newNode);
    }

    private void moveNodeToPassive(Node oldNode) {
        activeView.removeNode(oldNode);
        passiveView.addNode(oldNode);
        triggerNotification(
                new NeighDown(oldNode.getAddress(), oldNode.getLayer(), this.layer));
        logger.info("Node down: " + oldNode);
    }

    private void changeFather(Node oldFather, Node newFather) {
        activeView.removeNode(oldFather);
        passiveView.removeNode(newFather);

        activeView.addNode(newFather);
        passiveView.addNode(oldFather);

        triggerNotification(new FatherNotification(oldFather.getAddress(), oldFather.getLayer(),
                newFather.getAddress(), newFather.getLayer()));

        logger.info("Father change: newFather=" + newFather + ", oldFather=" + oldFather);
    }

    private void changeBrother(Node oldBrother, Node newBrother) {
        activeView.removeNode(oldBrother);
        passiveView.removeNode(newBrother);

        activeView.addNode(newBrother);
        passiveView.addNode(oldBrother);

        triggerNotification(new BrotherNotification(oldBrother.getAddress(), newBrother.getAddress()));
        logger.info("Brother change: newBrother="+newBrother + ", oldBrother="+oldBrother);
    }

    /*--------------------------------- Event handlers ---------------------------------- */

    /*--------------------------------- Messages ---------------------------------------- */


    private void uponReceiveJoin(JoinMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received " + msg + " from " + from);
        sendMessage(new DisconnectMessage(newSelf()), from, TCPChannel.CONNECTION_IN);
        logger.debug("Sent DisconnectMessage to {}", from);
        ForwardJoinMessage fjm = new ForwardJoinMessage(msg.getNode(), (short) (layerRW+1), maxLayers, maxNeighs);
        uponReceiveForwardJoin(fjm, myself, sourceProto, channelId);

    }

    private void uponReceiveForwardJoin(ForwardJoinMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received " + msg + " from " + from);
        if(!from.equals(myself) && !amConnectedTo(from) && !isConnectedToMe(from)) {
            sendMessage(new DisconnectMessage(newSelf()),
                    from, TCPChannel.CONNECTION_IN);
            logger.debug("Sent DisconnectMessage to {}", from);
        }

        addNodesToState(msg.getLayeredNodes());
        if(myself.equals(msg.getNewNode().getAddress())) { //end of join procedure
            if (optimizedTop == null)
                optimizedTop = optimizeTopLayer();
        } else { //continue
            addNodesToFJ(msg);
            Host nextHop = null;
            if(msg.decrementTtl(myself) > 0 &&
                    (nextHop = getBestNextHop(msg.getNewNode().getAddress(), msg.getPath())) != null) {
                //logger.warn("Sending " + msg + " to " + nextHop);
                sendMessage(msg, nextHop);
                logger.debug("Sent ForwardJoinMessage to {}", nextHop);
            } else {
                short nextLayer = computeNextLayer(msg.getLayeredNodes(), msg.getNewNode().getLayer());
                msg.reset(layerRW, nextLayer);
                nextHop = getBestNode(msg.getLayeredNodes().layer(nextLayer).keySet(), msg.getNewNode().getAddress());
                //logger.warn("Sending " + msg + " to " + nextHop + " in next layer " + nextLayer);
                if(isConnectedToMe(nextHop))
                    sendMessage(msg, nextHop, TCPChannel.CONNECTION_IN);
                else
                    sendMessage(msg, nextHop);
                logger.debug("Sent ForwardJoinMessage to {}", nextHop);
            }
        }
    }

    private Host getBestNode(Set<Host> nodes, Host target) {
        if(!nodes.isEmpty()) {
            PriorityQueue<Host> sortedConnected = new PriorityQueue<>(HostComp.host_comp.getInstance(target));
            sortedConnected.addAll(nodes);
            if (!sortedConnected.isEmpty())
                return sortedConnected.poll();
        }
        return null;
    }

    private Host getBestNextHop(Host target, List<Host> toExclude) {
        Set<Host> allConnectedNodes = new HashSet<>();
        allConnectedNodes.addAll(activeView.layer(this.layer).keySet());
        allConnectedNodes.addAll(controlView.layer(this.layer).keySet());
        allConnectedNodes.removeAll(toExclude);
        return getBestNode(allConnectedNodes, target);
    }

    private short addNodesToFJ(ForwardJoinMessage msg) {
        short nextLayer = computeNextLayer(activeView, msg.getNewNode().getLayer());
        for(Node n : activeView.layer(nextLayer).values())
            msg.addNeigh(n);

        msg.addNeigh(newSelf());

        return nextLayer;
    }

    private short computeNextLayer(LayeredView view, short targetLayer) {
        NavigableMap<Short, Map<Host, Node>> subView = view.getLayeredNodes()
                .subMap(this.layer, false, targetLayer, true);
        if(!subView.isEmpty())
            return subView.firstKey();
        return this.layer;
    }

    private void addNodesToState(LayeredView nodes) {
        Map<Host, Node> flatActiveView = activeView.flatView();
        for(Node n : nodes.flatView().values()) {
            if(!n.getAddress().equals(myself)) {
                if (!flatActiveView.containsKey(n.getAddress())) {
                    passiveView.addOrUpdateNode(n);
                } else {
                    activeView.updateNode(n);
                }
            }
        }

        passiveView.trim(maxLayers);
        for(short l : passiveView.getSortedLayers()) {
            PriorityQueue<Node> nodesInLayer = passiveView.sortedLayer(Node.invertedDistanceComparator(myself), l);
            while(nodesInLayer.size() > passiveView.maxCapacityInLayer(l))
                passiveView.removeNode(nodesInLayer.poll());
        }
    }

    private boolean isConnectedToMe(Host host) {
        return controlView.contains(host);
    }

    private boolean amConnectedTo(Host host) {
        return activeView.contains(host);
    }

    private LayeredView computeSample(Host target) {
        LayeredView sample = new LayeredView(k_a+k_p, this.layer);
        PriorityQueue<Node> sortedFlatView = passiveView.sortedFlatView(Node.distanceComparator(target));
        for(int i = 0; i < k_p && !sortedFlatView.isEmpty(); i++) {
            Node n = sortedFlatView.poll();
            if(!n.getAddress().equals(target))
                sample.addNode(n);
        }
        sortedFlatView = activeView.sortedFlatView(Node.distanceComparator(target));
        for(int i = 0; i < k_a && !sortedFlatView.isEmpty(); i++) {
            Node n = sortedFlatView.poll();
            if(!n.getAddress().equals(target))
                sample.addNode(n);
        }

        sample.addNode(newSelf());

        return sample;
    }

    private void uponReceiveShuffleReply(ShuffleReplyMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received " + msg + " from " + from);
        addNodesToState(msg.getSample());
        if(!amConnectedTo(from))
            closeConnection(from);
    }

    private void uponReceiveShuffle(ShuffleMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received " + msg + " from " + from);
        addNodesToState(msg.getSample());
        LayeredView sample = computeSample(from);
        int mode = TCPChannel.CONNECTION_IN;
        if(amConnectedTo(from) && !isConnectedToMe(from))
            mode = TCPChannel.CONNECTION_OUT;

        sendMessage(new ShuffleReplyMessage(sample), from, mode);
        logger.debug("Sent ShuffleReplyMessage to {}", from);
    }

    private void uponReceiveDisconnect(DisconnectMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received " + msg + " from " + from);
        if(optimizedTop != null && !optimizedTop.getAddress().equals(from) && !activeView.contains(from)) {
            closeConnection(from);
            passiveView.updateNode(msg.getNode());
        }
    }

    private void uponReceiveFather(FatherMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received " + msg + " from " + from);
        if(msg.getNewFather().getAddress().equals(myself) && !activeView.contains(msg.getChild())) {
            addNodeToActiveAndControl(msg.getChild());

            msg.updateNewFather(newSelf());
            sendMessage(msg, from, TCPChannel.CONNECTION_IN);
            logger.debug("Sent FatherMessage to {}", from);

        } else if(msg.getChild().getAddress().equals(myself)) {

            if(!msg.getOldFather().equals(msg.getChild()) && activeView.contains(msg.getOldFather())) {
                changeFather(msg.getOldFather(), msg.getNewFather());
                closeConnection(msg.getOldFather().getAddress());
            } else {
                addNodeToActive(msg.getNewFather());
            }

            optimizedTop = null;
        }
    }

    private void uponReceiveHello(HelloNeighborMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received " + msg + " from " + from);
        Node neighbour = msg.getNeighbour();
        assert (!controlView.contains(neighbour));
        controlView.addNode(neighbour);
        if(!passiveView.updateNode(neighbour))
            activeView.updateNode(neighbour);

        sendMessage(new HelloNeighborReplyMessage(newSelf()), from, TCPChannel.CONNECTION_IN);
        logger.debug("Sent HelloNeighborReplyMessage to {}", from);
    }

    private void uponReceiveHelloReply(HelloNeighborReplyMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received " + msg + " from " + from);
        Node neighbour = msg.getNeighbour();
        //assert (activeView.contains(neighbour));
        if(activeView.isFull(this.layer)) {
            PriorityQueue<Node> sortedActiveLayer = activeView.sortedLayer(Node.invertedDistanceComparator(myself), this.layer);
            Node toRemove = sortedActiveLayer.poll();
            closeConnection(toRemove.getAddress());
            changeBrother(toRemove, neighbour);
        } else {
            addNodeToActive(neighbour);
        }

        optimizedLayer = null;
    }



    /*--------------------------------- Timers ------------------------------------------ */

    private void uponTimeout(JoinTimeout timer, long timerId) {
        if(activeView.totalSize() == 0) {
            logger.warn("Retrying Join");
            JoinMessage m = new JoinMessage(newSelf());
            sendMessage(m, timer.getContacts().get(0));
            logger.debug("Sent JoinMessage to {}", timer.getContacts().get(0));
            timer.incCount();
            setupTimer(timer, (timeout * (this.layer) * layerRW) * timer.getCount());
        }
    }

    private Node fillActiveView() {
        Node optimized = null;
        if(!activeView.isFull(this.layer)) {
            PriorityQueue<Node> sortedPassiveLayerNodes = passiveView.sortedLayer(Node.distanceComparator(myself), this.layer);
            Node candidate = sortedPassiveLayerNodes.poll();
            if(candidate != null) {
                sendMessage(new HelloNeighborMessage(newSelf()), candidate.getAddress());
                logger.debug("Sent HelloNeighborMessage to {}", candidate.getAddress());
                optimized = candidate;
            }
        }
        return optimized;
    }

   private void uponFillView(FillViewTimer timer, long timerId) {
        if(optimizedLayer == null)
            optimizedLayer = fillActiveView();
   }

    private void printState() {
        logger.debug("---------------------------------");
        logger.debug("Self: " + myself + " " + layer);
        logger.debug("ACTIVE: " + activeView);
        logger.debug("PASSIVE: " + passiveView);
        logger.debug("CONTROL " + controlView);
        logger.debug("---------------------------------");
    }

    private void uponNeighShuffle(NeighShuffle timer, long timerId) {

        //printState();

        for(Node n : passiveView.flatView().values())
            n.incrementAge();
        Map<Host, Node> flatActiveView = activeView.flatView();
        for(Node n : flatActiveView.values())
            n.incrementAge();

        if(!flatActiveView.isEmpty()) {
            PriorityQueue<Node> sortedFlatActiveView = new PriorityQueue<>(flatActiveView.size(), Node.invertedAgeComparator());
            sortedFlatActiveView.addAll(flatActiveView.values());
            Host target = sortedFlatActiveView.poll().getAddress();
            LayeredView sample = computeSample(target);

            sendMessage(new ShuffleMessage(sample), target);
            logger.debug("Sent ShuffleMessage to {}", target);
        }

    }

    private Node optimizeTopLayer() {
        Node optimized = null;
        NavigableSet<Short> topActiveLayers = activeView.getTopLayers();
        NavigableSet<Short> topPassiveLayers = new TreeSet<>(passiveView.getTopLayers());
        if(!topPassiveLayers.isEmpty()) {
            while(!topPassiveLayers.isEmpty()) {
                Node bestCandidate = null;
                Node tochange = null;
                if(!topActiveLayers.isEmpty() && !activeView.layer(topActiveLayers.last()).isEmpty()) {
                    bestCandidate = activeView.sortedLayer(Node.invertedDistanceComparator(myself), topActiveLayers.last()).poll();
                    tochange = bestCandidate;
                }else {
                    while(!topPassiveLayers.isEmpty() && (bestCandidate == null || bestCandidate.isDead())) {
                        PriorityQueue<Node> sorted = passiveView.sortedLayer(Node.distanceComparator(myself), topPassiveLayers.pollLast());
                        if(sorted.isEmpty())
                            bestCandidate = null;
                        else
                            bestCandidate = sorted.poll();
                    }
                }

                //logger.warn("Best="+bestCandidate);

                //if optimizing candidate = current father
                //otherwise, candidate is the best father in the passive
                while(!topPassiveLayers.isEmpty()) { //explore tree
                    short lastTopPassiveLayer = topPassiveLayers.pollLast(); //remove the bottom
                    PriorityQueue<Node> sortedPassiveLayerNodes = passiveView.sortedLayer(
                            //get all passive in layer sorted towards the candidate
                            Node.distanceComparator(bestCandidate.getAddress()), lastTopPassiveLayer);
                    //get all active as well
                    sortedPassiveLayerNodes.addAll(activeView.layer(lastTopPassiveLayer).values());

                    //logger.warn("lastTopPassiveLayer=" + lastTopPassiveLayer);
                    //if there are nodes
                    if(!sortedPassiveLayerNodes.isEmpty()) {
                        Node bestCandidateBestFather = sortedPassiveLayerNodes.poll(); //get the candidates father

                        //logger.warn("BestsFather="+bestCandidateBestFather);
                        //get my best grandparent
                        sortedPassiveLayerNodes = passiveView.sortedLayer(
                                Node.distanceComparator(myself), lastTopPassiveLayer);
                        sortedPassiveLayerNodes.addAll(activeView.layer(lastTopPassiveLayer).values());
                        Node myBestGrandFather = sortedPassiveLayerNodes.poll();

                        //logger.warn("MyBestsGrandFather="+myBestGrandFather);
                        //if candidate father != mygrantparentt then I am in the wrong branch
                        if(!bestCandidateBestFather.equals(myBestGrandFather) && !myBestGrandFather.isDead()) //change branch
                            bestCandidate = myBestGrandFather;
                        //otherwise, I see if I can go lower on the tree, or I need to continue exploring
                        else if((bestCandidate.getLayer() < myBestGrandFather.getLayer() || bestCandidate.isDead())
                                && !myBestGrandFather.isDead() )
                            //go lower on the tree
                            bestCandidate = myBestGrandFather;
                    }
                }


                //logger.debug("On optimization:");
                //logger.debug("tochange="+tochange);
                //logger.debug("bestCandidate="+bestCandidate);
                if(bestCandidate != null) {
                    logger.debug("Optimizing father {} for {}", tochange, bestCandidate);
                    if (tochange == null) {
                        Node self = newSelf();
                        sendMessage(new FatherMessage(self, bestCandidate, self), bestCandidate.getAddress());
                        logger.debug("Sent FatherMessage to {}", bestCandidate.getAddress());
                        optimized = bestCandidate;
                    } else if (!tochange.equals(bestCandidate)) {
                        sendMessage(new FatherMessage(tochange, bestCandidate, newSelf()), bestCandidate.getAddress());
                        logger.debug("Sent FatherMessage to {}", bestCandidate.getAddress());
                        optimized = bestCandidate;
                    }
                }
            }
        }
        return optimized;
    }

    private Node optimizeLayer() {
        Node optimized = null;
        if(activeView.isFull(this.layer) && passiveView.layerNodesSize(this.layer) > 0) {
            PriorityQueue<Node> sortedPassiveLayer = passiveView.sortedLayer
                    (Node.distanceComparator(myself), this.layer);
            PriorityQueue<Node> sortedActiveLayer = activeView.sortedLayer(
                    Node.invertedDistanceComparator(myself), this.layer);
            Node candidate = sortedPassiveLayer.poll();
            Node old = sortedActiveLayer.poll();
            if(host_comp.compareScore(candidate.getAddress(), old.getAddress()) < 0) {
                sendMessage(new HelloNeighborMessage(newSelf()), candidate.getAddress());
                logger.debug("Sent HelloNeighborMessage to {}", candidate.getAddress());
                optimized = candidate;
            }
        }
        return optimized;
    }

    private void uponOptimization(OptimizationTimer timer, long timerId) {
        if(optimizedTop == null)
            optimizedTop = optimizeTopLayer();
        if(optimizedLayer == null)
            optimizedLayer = optimizeLayer();
    }

    private void uponLongDistanceShuffle(LongDistanceShuffle timer, long timerId) {
        for(Node n : activeView.flatView().values())
            n.incrementAge();
        Map<Host, Node> flatPassiveView = passiveView.flatView();
        for(Node n : flatPassiveView.values())
            n.incrementAge();

        if(!flatPassiveView.isEmpty()) {
            PriorityQueue<Node> sortedFlatPassiveView = new PriorityQueue<>(flatPassiveView.size(), Node.invertedAgeAndDistanceComparator(myself));
            sortedFlatPassiveView.addAll(flatPassiveView.values());
            Node target = null;
            while(!sortedFlatPassiveView.isEmpty() && (target == null || target.isDead()))
                target = sortedFlatPassiveView.poll();

            if(target != null) {
                LayeredView sample = computeSample(target.getAddress());

                sendMessage(new ShuffleMessage(sample), target.getAddress());
                logger.debug("Sent ShuffleMessage to {}", target);
            }
        }
    }

    /* --------------------------------- Channel Events ---------------------------- */

    private void repair(Node dead) {
        if(dead.getLayer() < this.layer) {
            if(optimizedTop == null)
                optimizedTop = optimizeTopLayer();
        } else if(dead.getLayer() == this.layer) {
            if(optimizedLayer == null)
                optimizedLayer = fillActiveView();

        } //else ignore
        triggerNotification(new NeighDown(dead.getAddress(), dead.getLayer(), this.layer));
        logger.info("Node down " + dead);
    }

    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        //logger.trace("Received " + event);

        Node dead = activeView.removeHost(event.getNode());
        if (dead != null) {
            if(dead.getLayer() <= this.layer)
                repair(dead);
            else
                activeView.addNode(dead); //if true, then the InConnectionDown must remove this
        } else if(passiveView.contains(event.getNode())) {
            passiveView.markDead(event.getNode());
            if(optimizedLayer != null && optimizedLayer.getAddress().equals(event.getNode()))
                optimizedLayer = null;
            if(optimizedTop != null && optimizedTop.getAddress().equals(event.getNode()))
                optimizedTop = null;

        }


    }

    private void uponOutConnectionFailed(OutConnectionFailed event, int channelId) {
        //logger.trace("Received " + event);
        passiveView.markDead(event.getNode());
        if(optimizedLayer != null && optimizedLayer.getAddress().equals(event.getNode()))
            optimizedLayer = null;
        if(optimizedTop != null && optimizedTop.getAddress().equals(event.getNode()))
            optimizedTop = null;


    }

    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        //logger.trace("Received " + event);
    }

    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        //logger.trace("Received " + event);
    }

    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        //logger.trace("Received " + event);
        Node dead = controlView.removeHost(event.getNode());
        if(dead != null && activeView.removeNode(dead)) {
            repair(dead);
        }
    }

    /* -------------------------------------- Init --------------------------------- */

    @Override
    public void init(Properties properties) {

        //Setup configuration params
        this.layer = Short.parseShort(properties.getProperty("layer", "-1"));
        this.layerRW = Short.parseShort(properties.getProperty("layerRW", "3"));

        this.maxLayers = Short.parseShort(properties.getProperty("maxLayers", "5"));
        this.maxNeighs = Short.parseShort(properties.getProperty("maxNeighs", "4"));
        //this.maxAccept = Short.parseShort(properties.getProperty("maxAccept", "3"));

        this.n_neighs = Short.parseShort(properties.getProperty("layerNeighs", "3"));
        this.b_neighs = Short.parseShort(properties.getProperty("backupNeighs", "4"));

        this.k_a = Short.parseShort(properties.getProperty("k_a", "2"));
        this.k_p = Short.parseShort(properties.getProperty("k_p", "4"));
        //this.maxAge = Short.parseShort(properties.getProperty("maxAge", "50"));
        //for comparing Ips: score(192.168.1.1, 192.168.2.1) : target = 192.168.1.2 -> 256
        // target : 192.168.1.2 score(192.168.1.1, 192.168.1.XX) <= 255
        //this.layerWeight = Integer.parseInt(properties.getProperty("layerWeight", "255"));

        //100 milliseconds
        this.timeout = Integer.parseInt(properties.getProperty("timeout", "100"));
        this.shuffleTime = Integer.parseInt(properties.getProperty("shuffleTime", "1000"));
        int fillActive = Integer.parseInt(properties.getProperty("fillActive", "1000"));
        this.optimizeTime = Integer.parseInt(properties.getProperty("optimizeTime", "2000"));
        this.longDistanceShuffleTime = Integer.parseInt(properties.getProperty("longDistanceShuffleTime", "10000"));

        if(layer < 0) {
            System.err.println("Invalid layer configuration: layer=" + layer + " , use a value >= 0 for layer=");
            System.exit(-1);
        }

        //Init state
        host_comp = HostComp.host_comp.getInstance(myself);
        passiveView = new LayeredView(this.b_neighs, this.layer);
        activeView = new LayeredView(this.n_neighs, this.layer);
        controlView = new LayeredView(this.b_neighs+this.n_neighs, this.layer);
        timestamp = 0;


        if (properties.containsKey("contacts")) {
            try {
                String[] allContacts = properties.getProperty("contacts").split(",");

                List<Host> contacts = new ArrayList<>(allContacts.length);
                for(String contact : allContacts) {
                    String[] hostElems = contact.split(":");
                   contacts.add(new Host(InetAddress.getByName(hostElems[0]), Short.parseShort(hostElems[1])));
                }

                JoinMessage m = new JoinMessage(newSelf());
                sendMessage(m, contacts.get(0));
                logger.debug("Sent JoinMessage to {}", contacts.get(0));
                setupTimer(new JoinTimeout(contacts), timeout*(this.layer)*layerRW);
                logger.trace("Sent " + m + " to " + contacts.get(0));
            } catch (Exception e) {
                System.err.println("Invalid contact on configuration: '" + properties.getProperty("contacts"));
                e.printStackTrace();
                System.exit(-1);
            }
        }

        logger.info("Hello, I am "+ myself + " in layer " + this.layer);

        setupPeriodicTimer(new NeighShuffle(), shuffleTime, shuffleTime);
        setupPeriodicTimer(new OptimizationTimer(), optimizeTime, optimizeTime);
        setupPeriodicTimer(new LongDistanceShuffle(), longDistanceShuffleTime, longDistanceShuffleTime);
        setupPeriodicTimer(new FillViewTimer(), fillActive, fillActive);

    }

}
