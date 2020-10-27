package protocols.overlays.hyparview.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ShuffleReplyMessage extends ProtoMessage {
    public final static short MSG_CODE = 4078;

    private short seqnum;
    private final List<Host> sample;

    public ShuffleReplyMessage(Collection<Host> peers, short seqnum) {
        super(ShuffleReplyMessage.MSG_CODE);
        this.sample = new ArrayList<>(peers);
        this.seqnum = seqnum;
    }

    @Override
    public String toString() {
        return "ShuffleReplyMessage{" +
                "seqN=" + seqnum +
                ", sample=" + sample +
                '}';
    }

    public List<Host> getSample() {
        return sample;
    }

    public short getSeqnum() {
        return seqnum;
    }

    public static final ISerializer<ShuffleReplyMessage> serializer = new ISerializer<ShuffleReplyMessage>() {
        @Override
        public void serialize(ShuffleReplyMessage msg, ByteBuf out) throws IOException {
            out.writeShort(msg.seqnum);
            out.writeShort(msg.sample.size());
            for(Host h: msg.sample) {
                Host.serializer.serialize(h, out);
            }
        }

        @Override
        public ShuffleReplyMessage deserialize(ByteBuf in) throws IOException {
            short seqnum = in.readShort();
            short size = in.readShort();
            List<Host> payload = new ArrayList<>(size);
            for(short i = 0; i < size; i++) {
                payload.add(Host.serializer.deserialize(in));
            }

            return new ShuffleReplyMessage(payload, seqnum);
        }

    };
}
