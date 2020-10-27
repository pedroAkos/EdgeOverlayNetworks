package protocols.tester;

import babel.exceptions.HandlerRegistrationException;
import babel.generic.GenericProtocol;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.overlays.biasLayerTree.notifications.NeighDown;
import protocols.overlays.biasLayerTree.notifications.NeighUp;
import protocols.overlays.cyclon.Cyclon;
import protocols.overlays.cyclon.requests.MembershipReply;
import protocols.overlays.cyclon.requests.MembershipRequest;
import protocols.overlays.tman.Tman;
import protocols.tester.utils.Timer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class TmanTester extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(TmanTester.class);

    public static final String PROTO_NAME = "TmanTester";
    public static final short PROTO_ID = 13;

    private static final short timerid = 1;


    private Set<Host> membership;


    public TmanTester() throws HandlerRegistrationException {
        super(PROTO_NAME, PROTO_ID);
        this.membership = new HashSet<>();

        registerReplyHandler(MembershipReply.REPLY_ID, this::uponGetPeers);

        registerTimerHandler(timerid, this::uponTimer);
    }


    private void uponGetPeers(MembershipReply reply, short sourceProto) {
        logger.debug("new Peers {}", reply.getPeers());
        Set<Host> retain = new HashSet<>(membership);
        retain.retainAll(reply.getPeers()); //the ones that did not change
        logger.debug("To retain: {}", retain);
        Set<Host> add = new HashSet<>(reply.getPeers());
        add.removeAll(retain); //the ones that we are going to add
        logger.debug("To add: {}", add);
        Set<Host> remove = new HashSet<>(membership);
        remove.removeAll(retain); //the ones to remove
        logger.debug("To remove: {}", remove);

        remove.forEach(host -> triggerNotification(new NeighDown(host, (short) -1, (short) -1)));
        add.forEach(host -> triggerNotification(new NeighUp(host, (short) -1, (short) -1)));

        membership.removeAll(remove);
        membership.addAll(add);
        logger.debug("Membership: {}", membership);

    }

    private void uponTimer(Timer timer, long timerid) {
        sendRequest(new MembershipRequest(-1), Tman.PROTOCOL_ID);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        setupPeriodicTimer(new Timer(timerid), 2000, 2000);
    }
}
