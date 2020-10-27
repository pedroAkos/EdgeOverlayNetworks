package protocols.overlays.xbot.utils;

import network.data.Host;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public interface Oracle {

    void monitor(Host host);

    void unMonitor(Host host);

    List<Pair<Host, Integer>> getCosts();

    int getCost(Host host);
}
