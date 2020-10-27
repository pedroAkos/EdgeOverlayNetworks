package protocols.overlays.biasLayerTree.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import protocols.overlays.biasLayerTree.utils.Node;

import java.io.IOException;

public class HelloNeighborReplyMessage extends ProtoMessage {
    public static final short MSG_CODE = 405;

    private Node neighbour;

    public HelloNeighborReplyMessage(Node neighbour) {
        super(MSG_CODE);
        this.neighbour = neighbour;
    }

    @Override
    public String toString() {
        return "HelloNeighborReplyMessage{" +
                "neigh=" + neighbour +
                "}";
    }

    public Node getNeighbour() {
        return neighbour;
    }

    public static final ISerializer<HelloNeighborReplyMessage> serializer = new ISerializer<HelloNeighborReplyMessage>() {

        @Override
        public void serialize(HelloNeighborReplyMessage m, ByteBuf out) throws IOException {
            Node.serializer.serialize(m.neighbour, out);
        }

        @Override
        public HelloNeighborReplyMessage deserialize(ByteBuf in) throws IOException {
            return new HelloNeighborReplyMessage(Node.serializer.deserialize(in));
        }

    };
}
