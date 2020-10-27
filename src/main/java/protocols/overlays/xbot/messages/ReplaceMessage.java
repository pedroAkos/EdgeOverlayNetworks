package protocols.overlays.xbot.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;

public class ReplaceMessage extends ProtoMessage {
    public static final short MSG_ID = 412;

    private final Host old;
    private final Host peer;

    @Override
    public String toString() {
        return "ReplaceMessage{" +
                "old=" + old +
                ", peer=" + peer +
                '}';
    }

    public ReplaceMessage(Host old, Host peer) {
        super(MSG_ID);
        this.old = old;
        this.peer = peer;
    }

    public Host getOld() {
        return old;
    }


    public Host getPeer() {
        return peer;
    }

    public static ISerializer<ReplaceMessage> serializer = new ISerializer<ReplaceMessage>() {
        @Override
        public void serialize(ReplaceMessage optimizationMessage, ByteBuf out) throws IOException {
            Host.serializer.serialize(optimizationMessage.old, out);
            Host.serializer.serialize(optimizationMessage.peer, out);
        }

        @Override
        public ReplaceMessage deserialize(ByteBuf in) throws IOException {
            return new ReplaceMessage(Host.serializer.deserialize(in),
                    Host.serializer.deserialize(in));
        }
    };
}
