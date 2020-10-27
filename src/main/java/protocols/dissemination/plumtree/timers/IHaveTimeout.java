package protocols.dissemination.plumtree.timers;

import babel.generic.ProtoTimer;

public class IHaveTimeout extends ProtoTimer {

    public static final short TIMER_ID = 301;

    private final int mid;

    public IHaveTimeout(int mid) {
        super(TIMER_ID);
        this.mid = mid;
    }

    public int getMid() {
        return mid;
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
