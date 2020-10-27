package utils;

import network.data.Host;

import java.net.InetAddress;
import java.util.Comparator;
import java.util.Set;

public interface IHostComparator extends Comparator<Host> {


    Host getMinHost(Set<Host> hosts);

    IHostComparator getInstance(Host target);

    int compareScore(Host h1, Host h2);


    class HostComparator implements IHostComparator {
        private Host target;

        public HostComparator() {

        }

        private HostComparator(Host target) {
            this.target = target;
        }

        public Host getMinHost(Set<Host> hosts) {
            Host min = null;
            int score = Integer.MAX_VALUE;
            for (Host h : hosts) {
                int tmp = Math.abs(ipToInt(h.getAddress()) - ipToInt(target.getAddress()));
                if (tmp < score) {
                    min = h;
                    score = tmp;
                }
            }
            return min;
        }

        @Override
        public IHostComparator getInstance(Host target) {
            return new HostComparator(target);
        }


        //from: https://www.experts-exchange.com/questions/20994482/Ip-address-to-int-and-vise-versa.html
        public static int ipToInt(InetAddress ipAddr) {
            int compacted = 0;
            byte[] bytes = ipAddr.getAddress();
            for (int i = 0; i < bytes.length; i++) {
                compacted += (bytes[i] * Math.pow(256, 4 - i - 1));
            }
            return compacted;
        }


        @Override
        public int compare(Host h1, Host h2) { //use this to sort messages (self -> target)

            int score = compareScore(h1, h2);
            if(score == 0)
                score = h1.compareTo(h2);
            return score;
        }

        public int compareScore(Host h1, Host h2) {
            int score1 = Math.abs(ipToInt(h1.getAddress()) - ipToInt(target.getAddress()));
            int score2 = Math.abs(ipToInt(h2.getAddress()) - ipToInt(target.getAddress()));
            return score1 - score2;
        }
    }

    class HostPortComparator implements IHostComparator {

        private Host target;

        private short targetId;

        public HostPortComparator(){

        };

        private HostPortComparator(Host target) {
            this.target = target;
            targetId = extractId(this.target.getPort());
        }

        //port numbers are assumed to be in the format 10lid
        private short extractId(int port){
            short id = (short)(port - 10000);

            while(id >= 100)
                id -= 100;
            return id;
        }

        @Override
        public Host getMinHost(Set<Host> hosts) {
            Host min = null;
            int score = Integer.MAX_VALUE;
            for(Host h : hosts) {
                int tmp = Math.abs(extractId(h.getPort()) - targetId);
                if(tmp < score) {
                    min = h;
                    score = tmp;
                }
            }
            return min;
        }

        @Override
        public IHostComparator getInstance(Host target) {
            return new HostPortComparator(target);
        }

        @Override
        public int compare(Host h1, Host h2) { //use this to sort messages (self -> target)

            int score = compareScore(h1, h2);
            if(score == 0) {
                score = h1.compareTo(h2);
            }
            return score;
        }

        public int compareScore(Host h1, Host h2) {
            int score1 = Math.abs(extractId(h1.getPort()) - targetId);
            int score2 = Math.abs(extractId(h2.getPort()) - targetId);
            //System.err.println("h1=" + h1+ ":" +score1+ ", h2="+h2+":"+score2);
            return score1 - score2;

        }
    }
}
