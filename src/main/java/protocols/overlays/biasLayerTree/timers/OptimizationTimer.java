package protocols.overlays.biasLayerTree.timers;


import babel.generic.ProtoTimer;

public class OptimizationTimer extends ProtoTimer {
    public static final short TIMER_CODE = 403;

    public OptimizationTimer() {
        super(TIMER_CODE);
    }

    @Override
    public OptimizationTimer clone() {
        return this;
    }
}
