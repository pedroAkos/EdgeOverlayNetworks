package protocols.overlays.tman.utils.profile;

import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class Profile implements Comparable<Profile> {

    private final short profileId;

    public Profile(short profileId) {
        this.profileId = profileId;
    }

    private static final Map<Short, ISerializer<Profile>> serializerMap = new HashMap<>();

    public static void registerSerializer(short profileId, ISerializer<Profile> profileSerializer) {
        serializerMap.putIfAbsent(profileId, profileSerializer);
    }




    public static ISerializer<Profile> serializer = new ISerializer<Profile>() {
        @Override
        public void serialize(Profile profile, ByteBuf out) throws IOException {
            out.writeShort(profile.profileId);
            serializerMap.get(profile.profileId).serialize(profile, out);
        }

        @Override
        public Profile deserialize(ByteBuf in) throws IOException {
            short profileId = in.readShort();
            return serializerMap.get(profileId).deserialize(in);
        }
    };

    public abstract Profile defaultProfile();

}
