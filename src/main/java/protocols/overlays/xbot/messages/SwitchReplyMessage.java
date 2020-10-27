package protocols.overlays.xbot.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;

public class SwitchReplyMessage extends ProtoMessage {
    public static final short MSG_ID = 415;

    private final Host peer;
    private final Host candidate;

    private final boolean answer;

    @Override
    public String toString() {
        return "SwitchReplyMessage{" +
                "peer=" + peer +
                ", candidate=" + candidate +
                ", answer=" + answer +
                '}';
    }

    public SwitchReplyMessage(Host peer, Host candidate, boolean answer) {
        super(MSG_ID);
        this.peer = peer;
        this.candidate = candidate;
        this.answer = answer;
    }

    public Host getPeer() {
        return peer;
    }

    public Host getCandidate() {
        return candidate;
    }

    public boolean getAnswer() {
        return answer;
    }

    public static ISerializer<SwitchReplyMessage> serializer = new ISerializer<SwitchReplyMessage>() {
        @Override
        public void serialize(SwitchReplyMessage optimizationMessage, ByteBuf out) throws IOException {
            Host.serializer.serialize(optimizationMessage.peer, out);
            Host.serializer.serialize(optimizationMessage.candidate, out);
            out.writeBoolean(optimizationMessage.answer);
        }

        @Override
        public SwitchReplyMessage deserialize(ByteBuf in) throws IOException {
            return new SwitchReplyMessage(Host.serializer.deserialize(in),
                    Host.serializer.deserialize(in),
                    in.readBoolean());
        }
    };
}
