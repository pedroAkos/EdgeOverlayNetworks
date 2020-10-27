package protocols.overlays.cyclon.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ShuffleReplyMessage extends ProtoMessage {
    public final static short MSG_ID = 421;
    private final Map<Host, Integer> subset;

    public ShuffleReplyMessage(Map<Host, Integer> subset) {
        super(MSG_ID);
        this.subset = subset;
    }

    public Map<Host, Integer> getSubset() {
        return subset;
    }

    @Override
    public String toString() {
        return "ShuffleReplyMessage{" +
                "subset=" + subset +
                '}';
    }

    public static ISerializer<ShuffleReplyMessage> serializer = new ISerializer<ShuffleReplyMessage>() {
        @Override
        public void serialize(ShuffleReplyMessage shuffleMessage, ByteBuf out) throws IOException {
            out.writeInt(shuffleMessage.subset.size());
            shuffleMessage.subset.forEach((h,a)-> {
                try {
                    Host.serializer.serialize(h, out);
                    out.writeInt(a);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        @Override
        public ShuffleReplyMessage deserialize(ByteBuf in) throws IOException {
            int size = in.readInt();
            Map<Host, Integer> subset = new HashMap<>(size, 1);
            for(int i = 0; i < size; i ++) {
                Host h = Host.serializer.deserialize(in);
                int age = in.readInt();
                subset.put(h, age);
            }
            return new ShuffleReplyMessage(subset);
        }
    };
}
