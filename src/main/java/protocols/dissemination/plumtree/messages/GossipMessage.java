package protocols.dissemination.plumtree.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.io.IOException;

public class GossipMessage extends ProtoMessage {

    public static final short MSG_ID = 301;

    private final int mid;
    private int round;

    private final short toDeliver;
    private final byte[] content;

    @Override
    public String toString() {
        return "GossipMessage{" +
                "mid=" + mid +
                ", round=" + round +
                '}';
    }

    public GossipMessage(int mid, int round, short toDeliver, byte[] content) {
        super(MSG_ID);
        this.mid = mid;
        this.round = round;
        this.toDeliver = toDeliver;
        this.content = content;
    }

    public int getRound() {
        return round;
    }

    public GossipMessage setRound(int round) {
        this.round = round;
        return this;
    }

    public int getMid() {
        return mid;
    }

    public short getToDeliver() {
        return toDeliver;
    }

    public byte[] getContent() {
        return content;
    }

    public static ISerializer<GossipMessage> serializer = new ISerializer<GossipMessage>() {
        @Override
        public void serialize(GossipMessage gossipMessage, ByteBuf out) throws IOException {
            out.writeInt(gossipMessage.mid);
            out.writeInt(gossipMessage.round);
            out.writeShort(gossipMessage.toDeliver);
            out.writeInt(gossipMessage.content.length);
            if(gossipMessage.content.length > 0) {
                out.writeBytes(gossipMessage.content);
            }
        }

        @Override
        public GossipMessage deserialize(ByteBuf in) throws IOException {
            int mid = in.readInt();
            int round = in.readInt();
            short toDeliver = in.readShort();
            int size = in.readInt();
            byte[] content = new byte[size];
            if(size > 0)
                in.readBytes(content);

            return new GossipMessage(mid, round, toDeliver, content);
        }
    };
}
