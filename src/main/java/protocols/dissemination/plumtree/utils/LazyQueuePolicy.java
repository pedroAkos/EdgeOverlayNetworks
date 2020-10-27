package protocols.dissemination.plumtree.utils;

import java.util.Queue;
import java.util.Set;

public interface LazyQueuePolicy {

    Set<AddressedIHaveMessage> apply(Queue<AddressedIHaveMessage> lazyQueue);
}
