package protocols.overlays.hyparview.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.net.UnknownHostException;

public class DisconnectMessage extends ProtoMessage {
    public final static short MSG_CODE = 404;


    public DisconnectMessage() {
        super(DisconnectMessage.MSG_CODE);
    }

    @Override
    public String toString() {
        return "DisconnectMessage{}";
    }

    public static final ISerializer<DisconnectMessage> serializer = new ISerializer<DisconnectMessage>() {
        @Override
        public void serialize(DisconnectMessage m, ByteBuf out) {

        }

        @Override
        public DisconnectMessage deserialize(ByteBuf in) throws UnknownHostException {

            return new DisconnectMessage();
        }

    };
}
