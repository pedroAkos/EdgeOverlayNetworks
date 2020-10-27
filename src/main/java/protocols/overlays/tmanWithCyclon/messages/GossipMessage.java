package protocols.overlays.tmanWithCyclon.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import protocols.overlays.tman.utils.Node;
import protocols.overlays.tman.utils.profile.Profile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;;

public class GossipMessage extends ProtoMessage {

    public static final short MSG_ID = 430;

    private final Collection<Node> nodes;

    public GossipMessage(Collection<Node> nodes) {
        super(MSG_ID);
        this.nodes = nodes;
    }

    @Override
    public String toString() {
        return "GossipMessage{" +
                "nodes=" + nodes +
                '}';
    }

    public Collection<Node> getNodes() {
        return nodes;
    }

    public static ISerializer<GossipMessage> serializer = new ISerializer<GossipMessage>() {
        @Override
        public void serialize(GossipMessage pushMessage, ByteBuf out) throws IOException {
            out.writeInt(pushMessage.nodes.size());
            pushMessage.nodes.forEach(node -> {
                try {
                    Host.serializer.serialize(node.getHost(), out);
                    Profile.serializer.serialize(node.getProfile(), out);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        @Override
        public GossipMessage deserialize(ByteBuf in) throws IOException {
            int size = in.readInt();
            Collection<Node> nodes = new ArrayList<>(size);
            for(int i = 0; i < size; i ++) {
                Host h = Host.serializer.deserialize(in);
                Profile profile = Profile.serializer.deserialize(in);
                nodes.add(new Node(h, profile));
            }
            return new GossipMessage(nodes);
        }
    };
}
