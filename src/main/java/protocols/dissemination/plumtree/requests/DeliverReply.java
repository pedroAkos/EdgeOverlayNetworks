package protocols.dissemination.plumtree.requests;

import babel.generic.ProtoReply;

public class DeliverReply extends ProtoReply {

    public static final short REPLY_ID = 301;

    private final byte[] msg;

    public DeliverReply(byte[] msg) {
        super(REPLY_ID);
        this.msg = msg;
    }

    public byte[] getMsg() {
        return msg;
    }
}
