package protocols.overlays.hyparview.timers;


import babel.generic.ProtoTimer;

public class ShuffleTimer extends ProtoTimer {
    public static final short TimerCode = 401;

    public ShuffleTimer() {
        super(ShuffleTimer.TimerCode);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
