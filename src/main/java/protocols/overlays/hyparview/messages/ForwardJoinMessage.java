package protocols.overlays.hyparview.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;
import java.net.UnknownHostException;

public class ForwardJoinMessage extends ProtoMessage {
    public final static short MSG_CODE = 403;

    private short ttl;
    private final Host newHost;

    public ForwardJoinMessage(short ttl, Host newHost) {
        super(ForwardJoinMessage.MSG_CODE);
        this.ttl = ttl;
        this.newHost = newHost;
    }

    @Override
    public String toString() {
        return "ForwardJoinMessage{" +
                "ttl=" + ttl +
                ", newHost=" + newHost +
                '}';
    }

    public Host getNewHost() {
        return newHost;
    }

    public short getTtl() {
        return ttl;
    }

    public short decrementTtl() {
        return ttl--; //decrement after returning
    }

    public static final ISerializer<ForwardJoinMessage> serializer = new ISerializer<ForwardJoinMessage>() {
        @Override
        public void serialize(ForwardJoinMessage m, ByteBuf out) throws IOException {
            out.writeShort(m.ttl);
            Host.serializer.serialize(m.newHost, out);
        }

        @Override
        public ForwardJoinMessage deserialize(ByteBuf in) throws IOException {
            short ttl = in.readShort();
            Host newHost = Host.serializer.deserialize(in);

            return new ForwardJoinMessage(ttl, newHost);
        }
    };
}
