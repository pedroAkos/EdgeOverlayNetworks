package protocols.overlays.xbot.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;

public class DisconnectWaitMessage extends ProtoMessage {
    public static final short MSG_ID = 416;


    public DisconnectWaitMessage() {
        super(MSG_ID);
    }

    public static ISerializer<DisconnectWaitMessage> serializer = new ISerializer<DisconnectWaitMessage>() {
        @Override
        public void serialize(DisconnectWaitMessage optimizationMessage, ByteBuf out) throws IOException {
        }

        @Override
        public DisconnectWaitMessage deserialize(ByteBuf in) throws IOException {
            return new DisconnectWaitMessage();
        }
    };
}
