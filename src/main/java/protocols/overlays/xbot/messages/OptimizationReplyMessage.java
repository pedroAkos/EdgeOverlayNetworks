package protocols.overlays.xbot.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;

public class OptimizationReplyMessage extends ProtoMessage {
    public static final short MSG_ID = 411;

    private final Host old;
    private final Host toDisconnect;
    private final boolean answer;

    @Override
    public String toString() {
        return "OptimizationReplyMessage{" +
                "old=" + old +
                ", toDisconnect=" + toDisconnect +
                ", answer=" + answer +
                '}';
    }

    public OptimizationReplyMessage(Host old, Host toDisconnect, boolean answer) {
        super(MSG_ID);
        this.old = old;
        this.toDisconnect = toDisconnect;
        this.answer = answer;
    }

    public Host getOld() {
        return old;
    }

    public Host getToDisconnect() {
        return toDisconnect;
    }

    public boolean getAnswer() {
        return answer;
    }

    public static ISerializer<OptimizationReplyMessage> serializer = new ISerializer<OptimizationReplyMessage>() {
        @Override
        public void serialize(OptimizationReplyMessage optimizationMessage, ByteBuf out) throws IOException {
            Host.serializer.serialize(optimizationMessage.old, out);
            Host.serializer.serialize(optimizationMessage.toDisconnect, out);
            out.writeBoolean(optimizationMessage.answer);
        }

        @Override
        public OptimizationReplyMessage deserialize(ByteBuf in) throws IOException {
            return new OptimizationReplyMessage(Host.serializer.deserialize(in),
                    Host.serializer.deserialize(in),
                    in.readBoolean());
        }
    };
}
