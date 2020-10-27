package protocols.overlays.tman.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import protocols.overlays.tman.utils.Node;
import protocols.overlays.tman.utils.profile.Profile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

public class GossipReplyMessage extends ProtoMessage {

    public static final short MSG_ID = 431;

    private final Collection<Node> nodes;

    public GossipReplyMessage(Collection<Node> nodes) {
        super(MSG_ID);
        this.nodes = nodes;
    }

    @Override
    public String toString() {
        return "GossipReplyMessage{" +
                "nodes=" + nodes +
                '}';
    }

    public Collection<Node> getNodes() {
        return nodes;
    }

    public static ISerializer<GossipReplyMessage> serializer = new ISerializer<GossipReplyMessage>() {
        @Override
        public void serialize(GossipReplyMessage pushMessage, ByteBuf out) throws IOException {
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
        public GossipReplyMessage deserialize(ByteBuf in) throws IOException {
            int size = in.readInt();
            Collection<Node> nodes = new ArrayList<>(size);
            for(int i = 0; i < size; i ++) {
                Host h = Host.serializer.deserialize(in);
                Profile profile = Profile.serializer.deserialize(in);
                nodes.add(new Node(h, profile));
            }
            return new GossipReplyMessage(nodes);
        }
    };
}
