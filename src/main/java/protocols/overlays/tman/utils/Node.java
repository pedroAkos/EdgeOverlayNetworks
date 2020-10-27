package protocols.overlays.tman.utils;

import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import protocols.overlays.tman.utils.profile.Profile;

import java.io.IOException;

public class Node {

    private final Host host;

    private Profile profile;

    public Node(Host host, Profile profile) {
        this.host = host;
        this.profile = profile;
    }

    public Host getHost() {
        return host;
    }

    @Override
    public String toString() {
        return "Node{" +
                "host=" + host +
                ", profile=" + profile +
                '}';
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return host.equals(node.host);
    }

    @Override
    public int hashCode() {
        return host.hashCode();
    }

    public static ISerializer<Node> serializer = new ISerializer<Node>() {
        @Override
        public void serialize(Node node, ByteBuf out) throws IOException {
            Host.serializer.serialize(node.host, out);
            Profile.serializer.serialize(node.profile, out);
        }

        @Override
        public Node deserialize(ByteBuf in) throws IOException {
            return new Node(Host.serializer.deserialize(in), Profile.serializer.deserialize(in));
        }
    };

}
