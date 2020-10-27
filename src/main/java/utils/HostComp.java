package utils;

public class HostComp {
    //TODO change to HostComparator
    public static IHostComparator host_comp;
    //IHostComparator host_comp = new HostComparator();

    public static void createHostComp(String type) {
        switch (type) {
            case "ip":
                host_comp = new IHostComparator.HostComparator();
                break;
            case "port":
                host_comp = new IHostComparator.HostPortComparator();
        }
    }
}
