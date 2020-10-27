package protocols.overlays.hyparview.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.net.UnknownHostException;

public class JoinReplyMessage extends ProtoMessage {
    public final static short MSG_CODE = 402;


    public JoinReplyMessage() {
        super(MSG_CODE);
    }

    @Override
    public String toString() {
        return "JoinReplyMessage{}";
    }

    public static final ISerializer<JoinReplyMessage> serializer = new ISerializer<JoinReplyMessage>() {
        @Override
        public void serialize(JoinReplyMessage m, ByteBuf out) {

        }

        @Override
        public JoinReplyMessage deserialize(ByteBuf in) throws UnknownHostException {
            return new JoinReplyMessage();
        }
    };
}
