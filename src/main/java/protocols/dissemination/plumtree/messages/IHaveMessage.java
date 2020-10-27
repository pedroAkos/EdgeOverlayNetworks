package protocols.dissemination.plumtree.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.io.IOException;

public class IHaveMessage extends ProtoMessage {

    public static final short MSG_ID = 304;

    private final int mid;
    private final int round;

    @Override
    public String toString() {
        return "IHaveMessage{" +
                "mid=" + mid +
                ", round=" + round +
                '}';
    }

    public IHaveMessage(int mid, int round) {
        super(MSG_ID);
        this.mid = mid;
        this.round = round;
    }

    public int getMid() {
        return mid;
    }

    public int getRound() {
        return round;
    }

    public static ISerializer<IHaveMessage> serializer = new ISerializer<IHaveMessage>() {
        @Override
        public void serialize(IHaveMessage iHaveMessage, ByteBuf out) throws IOException {
            out.writeInt(iHaveMessage.mid);
            out.writeInt(iHaveMessage.round);
        }

        @Override
        public IHaveMessage deserialize(ByteBuf in) throws IOException {
            return new IHaveMessage(in.readInt(), in.readInt());
        }
    };
}
