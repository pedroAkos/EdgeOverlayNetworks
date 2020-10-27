package protocols.overlays.cyclon.requests;

import babel.generic.ProtoRequest;

public class MembershipRequest extends ProtoRequest {

    public static final short REQUEST_ID = 420;

    private final int fanout;

    public MembershipRequest(int fanout) {
        super(REQUEST_ID);
        this.fanout = fanout;
    }

    public int getFanout() {
        return fanout;
    }
}
