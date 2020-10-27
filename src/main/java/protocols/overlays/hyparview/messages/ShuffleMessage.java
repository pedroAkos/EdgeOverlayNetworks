package protocols.overlays.hyparview.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ShuffleMessage extends ProtoMessage {
    public final static short MSG_CODE = 407;

    private short seqnum;
    private short ttl;
    private final List<Host> sample;
    private Host origin;

    public ShuffleMessage(Host self, Collection<Host> peers, short ttl, short seqnum) {
        super(ShuffleMessage.MSG_CODE);
        this.origin = self;
        this.sample = new ArrayList<>(peers);
        //this.sample.add(self);
        this.ttl = ttl;
        this.seqnum = seqnum;
    }

    public List<Host> getFullSample() {
        List<Host> full = new ArrayList<>(sample);
        full.add(origin);
        return full;
    }

    @Override
    public String toString() {
        return "ShuffleMessage{" +
                "origin="+origin +
                ", seqN=" + seqnum +
                ", ttl=" + ttl +
                ", sample=" + sample +
                '}';
    }

    public Host getOrigin() {
        return origin;
    }

    public short getTtl() {
        return ttl;
    }

    public short decrementTtl() {
        return ttl--;
    }

    public short getSeqnum() {
        return seqnum;
    }

    public static final ISerializer<ShuffleMessage> serializer = new ISerializer<ShuffleMessage>() {
        @Override
        public void serialize(ShuffleMessage msg, ByteBuf out) throws IOException {
            Host.serializer.serialize(msg.origin, out);
            out.writeShort(msg.seqnum);
            out.writeShort(msg.ttl);
            out.writeShort(msg.sample.size());
            for(Host h: msg.sample) {
                Host.serializer.serialize(h, out);
            }
        }

        @Override
        public ShuffleMessage deserialize(ByteBuf in) throws IOException {
            Host origin = Host.serializer.deserialize(in);
            short seqnum = in.readShort();
            short ttl = in.readShort();
            short size = in.readShort();
            List<Host> payload = new ArrayList<>(size);
            for(short i = 0; i < size; i++) {
                payload.add(Host.serializer.deserialize(in));
            }
            return new ShuffleMessage(origin, payload, ttl, seqnum);
        }

    };
}
