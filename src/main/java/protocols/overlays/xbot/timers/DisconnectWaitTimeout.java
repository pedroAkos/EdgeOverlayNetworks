package protocols.overlays.xbot.timers;

import babel.generic.ProtoTimer;
import network.data.Host;

public class DisconnectWaitTimeout extends ProtoTimer {

    public static final short TIMER_ID = 412;

    private final Host disconnected;
    public DisconnectWaitTimeout(Host disconnected) {
        super(TIMER_ID);
        this.disconnected = disconnected;
    }

    public Host getDisconnected() {
        return disconnected;
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
