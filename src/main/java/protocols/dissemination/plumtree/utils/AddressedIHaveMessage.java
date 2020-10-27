package protocols.dissemination.plumtree.utils;

import network.data.Host;
import protocols.dissemination.plumtree.messages.IHaveMessage;

public class AddressedIHaveMessage {
    public IHaveMessage msg;
    public Host to;

    public AddressedIHaveMessage(IHaveMessage msg, Host to) {
        this.msg = msg;
        this.to = to;
    }
}
