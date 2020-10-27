package protocols.dissemination.plumtree.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.io.IOException;

public class GraftMessage extends ProtoMessage {

    public static final short MSG_ID = 303;

    private final int mid;
    private final int round;

    @Override
    public String toString() {
        return "GraftMessage{" +
                "mid=" + mid +
                ", round=" + round +
                '}';
    }

    public GraftMessage(int mid, int round) {
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

    public static ISerializer<GraftMessage> serializer = new ISerializer<GraftMessage>() {
        @Override
        public void serialize(GraftMessage graftMessage, ByteBuf out) throws IOException {
            out.writeInt(graftMessage.mid);
            out.writeInt(graftMessage.round);
        }

        @Override
        public GraftMessage deserialize(ByteBuf in) throws IOException {
            return new GraftMessage(in.readInt(), in.readInt());
        }
    };
}
