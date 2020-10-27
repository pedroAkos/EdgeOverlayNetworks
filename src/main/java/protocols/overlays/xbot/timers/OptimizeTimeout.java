package protocols.overlays.xbot.timers;

import babel.generic.ProtoTimer;

public class OptimizeTimeout extends ProtoTimer {

    public static final short TIMER_ID = 411;

    public OptimizeTimeout() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
