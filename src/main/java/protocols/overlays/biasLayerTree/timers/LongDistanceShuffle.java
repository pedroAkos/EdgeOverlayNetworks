package protocols.overlays.biasLayerTree.timers;


import babel.generic.ProtoTimer;

public class LongDistanceShuffle extends ProtoTimer {
    public static final short TIMER_CODE = 404;

    public LongDistanceShuffle() {
        super(TIMER_CODE);
    }

    @Override
    public LongDistanceShuffle clone() {
        return this;
    }
}
