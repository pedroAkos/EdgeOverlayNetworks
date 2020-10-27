package protocols.dissemination.plumtree.utils;

import network.data.Host;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class HashProducer {

    private final ByteBuffer append;
    private final int off;
    private final int size;

    public HashProducer(Host self) {
        byte[] selfAddr = self.getAddress().getAddress();
        size = selfAddr.length + Integer.BYTES + Long.BYTES;
        append = ByteBuffer.allocate(size);
        append.put(selfAddr);
        append.putInt(self.getPort());
        this.off = append.arrayOffset();
    }


    public int hash(byte[] contents) {
        ByteBuffer buffer = ByteBuffer.allocate(contents.length + size);
        buffer.put(contents);
        append.putLong(off, System.currentTimeMillis());
        buffer.put(append);
        return Arrays.hashCode(buffer.array());
    }
}
