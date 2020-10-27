package protocols.overlays.tman.utils.profile;

import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import utils.HostComp;

import java.io.IOException;

public class IPAddrProfile extends Profile {

    public static final short PROFILE_ID = 1;

    private final Host target;
    private final Host address;

    public IPAddrProfile(Host target, Host address) {
        super(PROFILE_ID);
        this.target = target;
        this.address = address;
    }

    @Override
    public String toString() {
        return "IPAddrProfile{" +
                "address=" + address +
                '}';
    }

    @Override
    public Profile defaultProfile() {
        return new IPAddrProfile(target,null);
    }

    @Override
    public int compareTo(Profile o) {
        if(o instanceof IPAddrProfile) {
            if(((IPAddrProfile) o).address != null && this.address != null) {
                return HostComp.host_comp.getInstance(target).compareScore(address, ((IPAddrProfile) o).address);
            } else if(this.address == null) {
                return Integer.MAX_VALUE;
            } else
                return Integer.MIN_VALUE;
        }
        return 0;
    }

    public static ISerializer<Profile> generateSerializer(Host target) {
        return new StatefulISerializer(target);
    }

    private static class StatefulISerializer implements ISerializer<Profile> {

        private final Host target;

        private StatefulISerializer(Host target) {
            this.target = target;
        }

        @Override
        public void serialize(Profile ipAddrProfile, ByteBuf out) throws IOException {
            if(((IPAddrProfile)ipAddrProfile).address != null) {
                out.writeBoolean(true);
                Host.serializer.serialize(((IPAddrProfile)ipAddrProfile).address, out);
            } else {
                out.writeBoolean(false);
            }
        }

        @Override
        public IPAddrProfile deserialize(ByteBuf in) throws IOException {
            Host address = null;
            if(in.readBoolean()) {
                address = Host.serializer.deserialize(in);
            }
            return new IPAddrProfile(target, address);
        }
    }
}
