package protocols.overlays.tmanWithCyclon.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import protocols.overlays.tman.utils.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ShuffleMessage extends ProtoMessage {
    public final static short MSG_ID = 420;

    private final Map<Host, Integer> subset;
    private final Collection<Node> profiles;

    public ShuffleMessage(Map<Host, Integer> subset, Collection<Node> profiles) {
        super(MSG_ID);
        this.subset = subset;
        this.profiles = profiles;
    }

    public Map<Host, Integer> getSubset() {
        return subset;
    }

    public Collection<Node> getProfiles() {
        return profiles;
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
            shuffleMessage.profiles.forEach((h)-> {
                try {
                    Node.serializer.serialize(h, out);
                    out.writeInt(shuffleMessage.subset.get(h.getHost()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        @Override
        public ShuffleMessage deserialize(ByteBuf in) throws IOException {
            int size = in.readInt();
            Map<Host, Integer> subset = new HashMap<>(size, 1);
            Collection<Node> profiles = new ArrayList<>(size);
            for(int i = 0; i < size; i ++) {
                Node h = Node.serializer.deserialize(in);
                int age = in.readInt();
                subset.put(h.getHost(), age);
                profiles.add(h);
            }
            return new ShuffleMessage(subset, profiles);
        }
    };
}
