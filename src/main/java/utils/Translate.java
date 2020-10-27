package utils;

import java.util.HashMap;
import java.util.Map;

public class Translate {

    private static final Map<Short, String> idToName = new HashMap<>();

    public static String ProtoIdToName(short protoId) {
        return idToName.get(protoId);
    }

    public static void addId(short id, String name) {
        idToName.put(id, name);
    }

}
