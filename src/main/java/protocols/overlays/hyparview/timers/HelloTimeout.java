package protocols.overlays.hyparview.timers;


import babel.generic.ProtoTimer;

public class HelloTimeout extends ProtoTimer {
    public static final short TimerCode = 402;

    public HelloTimeout() {
        super(HelloTimeout.TimerCode);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
