package protocols.overlays.xbot.utils;

import io.netty.buffer.ByteBuf;
import network.data.Host;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UDPLatencyOracle implements Oracle {

    private static final Logger logger = LogManager.getLogger(UDPLatencyOracle.class);

    private final Set<Host> toMonitor;

    private final ConcurrentMap<InetAddress, Integer> costMap;
    private final ConcurrentMap<InetAddress, Long> onGoing;

    public UDPLatencyOracle(Host self, int oraclePort, long deltaMs) throws SocketException {
        costMap = new ConcurrentHashMap<>();
        onGoing = new ConcurrentHashMap<>();
        toMonitor = new HashSet<>();

        byte ping = 'i';
        byte pong = 'o';

        DatagramSocket socket = new DatagramSocket(oraclePort);
        byte[] receiveBuffer = new byte[5];
        ByteBuffer read = ByteBuffer.allocate(5);
        Thread receive = new Thread(() -> {
            while (true) {
                DatagramPacket p = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                try {
                    socket.receive(p);
                    read.put(p.getData());
                    read.rewind();
                    logger.trace("RECEIVED P={}   p.data={} p.dataLen={} write={} write.Array={}", p, p.getData(), p.getData().length, read, read.array());
                    int receivedPort = read.getInt();
                    byte pingpong = read.get();
                    Host received = new Host(p.getAddress(), receivedPort);
                    if(pingpong == ping) {
                        logger.trace("Received ping from {}", received);
                        read.rewind();
                        read.putInt(receivedPort);
                        read.put(pong);
                        p.setData(read.array());
                        p.setAddress(received.getAddress());
                        socket.send(p);
                        logger.trace("Sent pong to {}", p.getAddress());
                    } else if(pingpong == pong){
                        logger.trace("Received pong from {} onGoing={}", received, onGoing);
                        Long t = onGoing.remove(p.getAddress());
                        if(t != null) {
                            synchronized (toMonitor) {
                                if (toMonitor.contains(received)) {
                                    costMap.put(p.getAddress(), (int) (System.currentTimeMillis() - t));
                                } else {
                                    costMap.remove(p.getAddress());
                                }
                            }
                        } else {
                            logger.error("Received pong, but no ping is ongoing for host {}", received);
                        }
                    } else {
                        logger.error("Received unexpected ping/pong {}", pingpong);
                    }
                    read.rewind();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        receive.setName("Receiver");
        receive.start();


        byte[] sendBuffer = new byte[5];
        ByteBuffer write = ByteBuffer.allocate(5);
        write.putInt(self.getPort());
        write.put(ping);
        DatagramPacket p = new DatagramPacket(sendBuffer, sendBuffer.length);
        p.setData(write.array());
        logger.trace("TO SEND P={}   p.data={} p.dataLen={} write={} write.Array={}", p, p.getData(), p.getData().length, write, write.array());
        Thread send = new Thread(() -> {
            while (true) {
                try {
                    logger.trace("Sleeping deltaMs {}", deltaMs);
                    Thread.sleep(deltaMs);
                    Set<Host> targets;
                    synchronized (toMonitor) {
                        targets = new HashSet<>(toMonitor);
                    }
                    logger.trace("Targets={}", targets);
                    for(Host target : targets) {
                        if(!onGoing.containsKey(target.getAddress())) {
                            p.setAddress(target.getAddress());
                            p.setPort(oraclePort);
                            onGoing.putIfAbsent(target.getAddress(), System.currentTimeMillis());
                            socket.send(p);
                            logger.trace("Sent ping to {} onGoing={}", target, onGoing);
                        }
                    }

                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
        send.setName("Sender");
        send.start();

        logger.trace("Oracle up");
    }


    @Override
    public void monitor(Host host) {
        synchronized (toMonitor) {
            toMonitor.add(host);
        }
    }

    @Override
    public void unMonitor(Host host) {
        synchronized (toMonitor) {
            toMonitor.remove(host);
        }
    }

    @Override
    public List<Pair<Host, Integer>> getCosts() {
        List<Pair<Host, Integer>> costs;
        synchronized (toMonitor) {
            costs = new ArrayList<>(toMonitor.size());
            for(Host h : toMonitor)
                costs.add(new ImmutablePair<>(h, getCost(h)));
        }
        return costs;
    }

    @Override
    public int getCost(Host host) {
        return costMap.getOrDefault(host.getAddress(), Integer.MAX_VALUE);
    }
}
