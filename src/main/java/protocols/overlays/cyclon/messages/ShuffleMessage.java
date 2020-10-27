package protocols.overlays.cyclon.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ShuffleMessage extends ProtoMessage {
    public final static short MSG_ID = 420;

    private final Map<Host, Integer> subset;

    public ShuffleMessage(Map<Host, Integer> subset) {
        super(MSG_ID);
        this.subset = subset;
    }

    public Map<Host, Integer> getSubset() {
        return subset;
    }

    @Override
    public String toString() {
        return "ShuffleMessage{" +
                "subset=" + subset +
                '}';
    }

    public static ISerializer<ShuffleMessage> serializer = new ISerializer<ShuffleMessage>() {
        @Override
        public void serialize(ShuffleMessage shuffleMessage, ByteBuf out) throws IOException {
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
        public ShuffleMessage deserialize(ByteBuf in) throws IOException {
            int size = in.readInt();
            Map<Host, Integer> subset = new HashMap<>(size, 1);
            for(int i = 0; i < size; i ++) {
                Host h = Host.serializer.deserialize(in);
                int age = in.readInt();
                subset.put(h, age);
            }
            return new ShuffleMessage(subset);
        }
    };
}
