package protocols.tester.utils;

import babel.generic.ProtoTimer;

public class Timer extends ProtoTimer {

    public Timer(short id) {
        super(id);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
