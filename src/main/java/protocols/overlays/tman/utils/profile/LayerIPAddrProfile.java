package protocols.overlays.tman.utils.profile;

import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.HostComp;
import utils.IHostComparator;

import java.io.IOException;

public class LayerIPAddrProfile extends Profile {

    private static Logger logger = LogManager.getLogger(LayerIPAddrProfile.class);

    public static final short PROFILE_ID = 2;

    private final Host target;
    private final short targetLayer;

    private final Host address;
    private final short layer;

    IHostComparator comparator;

    public LayerIPAddrProfile(Host target, short targetLayer, Host address, short layer) {
        super(PROFILE_ID);
        this.target = target;
        this.targetLayer = targetLayer;
        this.address = address;
        this.layer = layer;
        comparator = HostComp.host_comp.getInstance(target);
    }

    @Override
    public String toString() {
        return "IPAddrProfile{" +
                "target= (" + target + ", "+ targetLayer +
                "), address=" + address +
                ", layer=" + layer +
                '}';
    }

    @Override
    public Profile defaultProfile() {
        return new LayerIPAddrProfile(target, targetLayer,null,  (short)-1);
    }

    public short getLayer() {
        return layer;
    }

    @Override
    public int compareTo(Profile o) {
        if(o instanceof LayerIPAddrProfile) {
            LayerIPAddrProfile layerIPAddrProfile = (LayerIPAddrProfile) o;
            if(layerIPAddrProfile.address != null && this.address != null) {
                int layerScore = Math.abs(layer-layerIPAddrProfile.layer);
                int score = comparator.compareScore(address, ((LayerIPAddrProfile) o).address)*(layerScore+1);
                return score;
            } else if(this.address == null) {
                return Integer.MAX_VALUE;
            } else
                return Integer.MIN_VALUE;
        }
        return 0;
    }

    public static ISerializer<Profile> generateSerializer(Host target, short targetLayer) {
        return new StatefulISerializer(target, targetLayer);
    }

    private static class StatefulISerializer implements ISerializer<Profile> {

        private final Host target;
        private final short targetLayer;

        private StatefulISerializer(Host target, short targetLayer) {
            this.target = target;
            this.targetLayer = targetLayer;
        }

        @Override
        public void serialize(Profile ipAddrProfile, ByteBuf out) throws IOException {
            if(((LayerIPAddrProfile)ipAddrProfile).address != null) {
                out.writeBoolean(true);
                Host.serializer.serialize(((LayerIPAddrProfile)ipAddrProfile).address, out);
                out.writeShort(((LayerIPAddrProfile) ipAddrProfile).layer);
            } else {
                out.writeBoolean(false);
            }
        }

        @Override
        public LayerIPAddrProfile deserialize(ByteBuf in) throws IOException {
            Host address = null;
            short layer = -1;
            if(in.readBoolean()) {
                address = Host.serializer.deserialize(in);
                layer = in.readShort();
            }
            return new LayerIPAddrProfile(target, targetLayer, address, layer);
        }
    }
}
