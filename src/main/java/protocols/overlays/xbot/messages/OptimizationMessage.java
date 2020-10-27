package protocols.overlays.xbot.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;

public class OptimizationMessage extends ProtoMessage {
    public static final short MSG_ID = 410;

    private final Host old;

    @Override
    public String toString() {
        return "OptimizationMessage{" +
                "old=" + old +
                '}';
    }

    public OptimizationMessage(Host old) {
        super(MSG_ID);
        this.old = old;
    }

    public Host getOld() {
        return old;
    }

    public static ISerializer<OptimizationMessage> serializer = new ISerializer<OptimizationMessage>() {
        @Override
        public void serialize(OptimizationMessage optimizationMessage, ByteBuf out) throws IOException {
            Host.serializer.serialize(optimizationMessage.old, out);
        }

        @Override
        public OptimizationMessage deserialize(ByteBuf in) throws IOException {
            return new OptimizationMessage(Host.serializer.deserialize(in));
        }
    };
}
