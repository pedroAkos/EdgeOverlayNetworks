package protocols.overlays.biasLayerTree.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import protocols.overlays.biasLayerTree.utils.Node;

import java.io.IOException;

public class FatherMessage extends ProtoMessage {

    public static final short MSG_CODE = 406;

    private Node oldFather;
    private Node newFather;
    private Node child;


    public FatherMessage(Node oldFather, Node newFather, Node child) {
        super(MSG_CODE);
        this.oldFather = oldFather;
        this.newFather = newFather;
        this.child = child;
    }

    @Override
    public String toString() {
        return "FatherMessage{" +
                "old=" + oldFather +
                ", new=" + newFather +
                ", child=" + child +
                "}";
    }

    public Node getChild() {
        return child;
    }

    public Node getNewFather() {
        return newFather;
    }

    public Node getOldFather() {
        return oldFather;
    }

    public void updateNewFather(Node newFather) {
        this.newFather = newFather;
    }

    public static ISerializer<FatherMessage> serializer = new ISerializer<FatherMessage>() {
        @Override
        public void serialize(FatherMessage fatherMessage, ByteBuf out) throws IOException {
            Node.serializer.serialize(fatherMessage.oldFather, out);
            Node.serializer.serialize(fatherMessage.newFather, out);
            Node.serializer.serialize(fatherMessage.child, out);
        }

        @Override
        public FatherMessage deserialize(ByteBuf in) throws IOException {
            Node oldFather = Node.serializer.deserialize(in);
            Node newFather = Node.serializer.deserialize(in);
            Node child = Node.serializer.deserialize(in);

            return new FatherMessage(oldFather, newFather, child);
        }

    };
}
