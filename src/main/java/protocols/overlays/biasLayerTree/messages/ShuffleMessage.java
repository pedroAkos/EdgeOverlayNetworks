package protocols.overlays.biasLayerTree.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import protocols.overlays.biasLayerTree.utils.LayeredView;

import java.io.IOException;

public class ShuffleMessage extends ProtoMessage {
    public static final short MSG_CODE = 409;

    private LayeredView sample;

    public ShuffleMessage(LayeredView sample) {
        super(MSG_CODE);
        this.sample = sample;
    }

    @Override
    public String toString() {
        return "ShuffleMessage={" +
                "sample=" + sample +
                "}";
    }

    public LayeredView getSample() {
        return sample;
    }

    public static ISerializer<ShuffleMessage> serializer = new ISerializer<ShuffleMessage>() {
        @Override
        public void serialize(ShuffleMessage shuffleMessage, ByteBuf out) throws IOException {
            LayeredView.serializer.serialize(shuffleMessage.sample, out);
        }

        @Override
        public ShuffleMessage deserialize(ByteBuf in) throws IOException {
            LayeredView sample = LayeredView.serializer.deserialize(in);

            return new ShuffleMessage(sample);
        }

    };
}
