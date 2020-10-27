package protocols.tester;

import babel.exceptions.HandlerRegistrationException;
import babel.generic.GenericProtocol;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.dissemination.plumtree.requests.BroadcastRequest;
import protocols.dissemination.plumtree.requests.DeliverReply;
import protocols.tester.utils.Timer;

import java.io.IOException;
import java.util.Properties;
import java.util.Random;

public class DisseminationConsumer extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(DisseminationConsumer.class);


    public static final String PROTO_NAME = "DisseminationConsumer";
    public static final short PROTO_ID = 11;

    private static final short timerid = 1;

    private int seqNum = 0;
    private final Host self;
    private final short disseminationProto;

    private final int payloadSize;
    private final Random rnd;
    private final int maxMsgs;

    public DisseminationConsumer(Host self, Properties properties) throws HandlerRegistrationException {
        super(PROTO_NAME, PROTO_ID);
        this.self = self;
        this.disseminationProto = Short.parseShort(properties.getProperty("disseminationProto"));

        this.payloadSize = Integer.parseInt(properties.getProperty("payloadSize", "0"));
        this.rnd = new Random();

        this.maxMsgs = Integer.parseInt(properties.getProperty("maxMsgs", "0"));

        registerReplyHandler(DeliverReply.REPLY_ID, this::uponDeliver);

        registerTimerHandler(timerid, this::uponSendMsg);
    }

    private void uponDeliver(DeliverReply reply, short sourceProto) {
        byte[] msg;
        if(payloadSize > 0) {
            msg = new byte[reply.getMsg().length - payloadSize];
            System.arraycopy(reply.getMsg(), payloadSize-1, msg, 0, reply.getMsg().length - payloadSize);
        } else
            msg = reply.getMsg();

        logger.info("Received: {}", new String(msg));
    }

    private void uponSendMsg(Timer timer, long timerId) {
        if(maxMsgs == 0 || seqNum < maxMsgs) {
            String tosend = String.format("Hello from %s seq num: %d", self, seqNum++);
            byte[] toSend;
            if (payloadSize > 0) {
                byte[] payload = new byte[payloadSize];
                rnd.nextBytes(payload);
                toSend = new byte[(payloadSize + tosend.getBytes().length)];
                System.arraycopy(payload, 0, toSend, 0, payloadSize);
                System.arraycopy(tosend.getBytes(), 0, toSend, payloadSize - 1, tosend.getBytes().length);
            } else
                toSend = tosend.getBytes();

            sendRequest(new BroadcastRequest(toSend), disseminationProto);
            logger.info("Sent: {}", tosend);
        }
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        int disseminationPeriod = Integer.parseInt(props.getProperty("disseminationPeriod", "2000"));
        if(Integer.parseInt(props.getProperty("layer")) == 0) {
            setupPeriodicTimer(new Timer(timerid), 60000, disseminationPeriod);
        } else if(Boolean.parseBoolean(props.getProperty("disseminate", "false"))) {
            setupPeriodicTimer(new Timer(timerid), 120000, disseminationPeriod);
        }
    }
}
