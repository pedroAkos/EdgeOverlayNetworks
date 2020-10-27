import babel.Babel;
import babel.exceptions.InvalidParameterException;
import channels.MultiLoggerChannelInitializer;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.dissemination.flood.FloodGossip;
import protocols.dissemination.plumtree.PlumTree;
import protocols.overlays.biasLayerTree.BiasLayeredTree;
import protocols.overlays.cyclon.Cyclon;
import protocols.overlays.hyparview.HyparView;
import protocols.overlays.tmanWithCyclon.TmanWithCyclon;
import protocols.overlays.xbot.Xbot;
import protocols.tester.CyclonTester;
import protocols.tester.DisseminationConsumer;
import protocols.tester.OverlayConsumer;
import protocols.tester.TmanTester;
import utils.BabelConfigValidator;
import utils.ExistsFileValidor;
import utils.InterfaceToIp;
import utils.Translate;

import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Main {

    public static class Args {

        @Parameter(names = {"--help", "-h"}, help = true)
        public boolean help;

        @Parameter(names = "-overlay")
        public String overlay;

        @Parameter(names = "-dissemination")
        public String dissemination;


        @Parameter(names = "-babelConfFile", description = "Babel configuration file" , validateWith = ExistsFileValidor.class)
        public String babelConf = DEFAULT_CONF;

        @Parameter(names = "-babelConf", description = "Babel configuration", validateWith = BabelConfigValidator.class)
        public List<String> babelArgs = new ArrayList<>();

    }




    static {
        System.setProperty("log4j.configurationFile", "log4j2.xml");
    }

    private static final String DEFAULT_CONF = "config/network_config.properties";
    public static final String PROTO_CHANNELS = "SharedTCP";

    private static final Logger logger = LogManager.getLogger(Main.class);


    public static void main(String[] args) throws Exception {

        Args cli = new Args();
        JCommander jc = JCommander.newBuilder().addObject(cli).build();

        try {
            jc.parse(args);
        } catch (ParameterException e) {
            logger.error(e.getMessage());
            e.usage();
            System.exit(1);
        }

        if(cli.help) {
            jc.usage();
            System.exit(1);
        }

        Babel babel = Babel.getInstance();
        Properties props = babel.loadConfig(cli.babelConf, cli.babelArgs.toArray(new String[0]));
        addInterfaceIp(props);
        props.setProperty("nThreads", "4");

        //babel.registerChannelInitializer(PROTO_CHANNELS, new MultiChannelInitializer());
        babel.registerChannelInitializer(PROTO_CHANNELS, new MultiLoggerChannelInitializer());
        Host myself =  new Host(InetAddress.getByName(props.getProperty("address")),
                Integer.parseInt(props.getProperty("port")));

        logger.info("Hello, I am {}", myself);

        logger.info("Loading overlay {}", cli.overlay);
        switch (cli.overlay) {
            case HyparView.PROTOCOL_NAME:
                Translate.addId(HyparView.PROTOCOL_ID, HyparView.PROTOCOL_NAME);
                HyparView hyparView = new HyparView(PROTO_CHANNELS, props, myself);
                babel.registerProtocol(hyparView);
                hyparView.init(props);
                break;
            case Xbot.PROTOCOL_NAME:
                Translate.addId(Xbot.PROTOCOL_ID, Xbot.PROTOCOL_NAME);
                Xbot xbot = new Xbot(PROTO_CHANNELS, props, myself);
                babel.registerProtocol(xbot);
                xbot.init(props);
                break;
            case BiasLayeredTree.PROTOCOL_NAME:
                Translate.addId(BiasLayeredTree.PROTOCOL_ID, BiasLayeredTree.PROTOCOL_NAME);
                BiasLayeredTree calm = new BiasLayeredTree(PROTO_CHANNELS, props, myself);
                babel.registerProtocol(calm);
                calm.init(props);
                break;
            case TmanWithCyclon.PROTOCOL_NAME:
                Translate.addId(TmanWithCyclon.PROTOCOL_ID, TmanWithCyclon.PROTOCOL_NAME);
                TmanWithCyclon tman = new TmanWithCyclon(PROTO_CHANNELS, props, myself);
                babel.registerProtocol(tman);
                tman.init(props);

                TmanTester tmanTester = new TmanTester();
                babel.registerProtocol(tmanTester);
                tmanTester.init(props);
                break;
            case Cyclon.PROTOCOL_NAME:
                Translate.addId(Cyclon.PROTOCOL_ID, Cyclon.PROTOCOL_NAME);
                Cyclon cyclon = new Cyclon(PROTO_CHANNELS, props, myself);
                babel.registerProtocol(cyclon);
                cyclon.init(props);

                CyclonTester cyclonTester = new CyclonTester();
                babel.registerProtocol(cyclonTester);
                cyclonTester.init(props);
                break;
            default:
                logger.error("Overlay {} is invalid", cli.overlay);
        }

        OverlayConsumer overlayConsumer = new OverlayConsumer(props);
        babel.registerProtocol(overlayConsumer);
        overlayConsumer.init(props);

        logger.info("Loading dissemination {}", cli.dissemination);
        switch (cli.dissemination) {
            case PlumTree.PROTOCOL_NAME:
                Translate.addId(PlumTree.PROTOCOL_ID, PlumTree.PROTOCOL_NAME);
                PlumTree plumTree = new PlumTree(PROTO_CHANNELS, props, myself);
                babel.registerProtocol(plumTree);
                plumTree.init(props);
                props.setProperty("disseminationProto", String.valueOf(PlumTree.PROTOCOL_ID));
                break;
            case FloodGossip.PROTO_NAME:
                Translate.addId(FloodGossip.PROTO_ID, FloodGossip.PROTO_NAME);
                FloodGossip floodGossip = new FloodGossip(PROTO_CHANNELS, props, myself);
                babel.registerProtocol(floodGossip);
                floodGossip.init(props);
                props.setProperty("disseminationProto", String.valueOf(FloodGossip.PROTO_ID));
                break;
            default:
                logger.error("Dissemination {} is invalid", cli.overlay);
        }

        DisseminationConsumer disseminationConsumer = new DisseminationConsumer(myself, props);
        babel.registerProtocol(disseminationConsumer);
        disseminationConsumer.init(props);

        babel.start();

        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                logger.info("Goodbye");
            }
        });

    }

    private static void addInterfaceIp(Properties props) throws SocketException, InvalidParameterException {
        String interfaceName;
        if ((interfaceName = props.getProperty("interface")) != null) {
            String ip = InterfaceToIp.getIpOfInterface(interfaceName);
            if(ip != null)
                props.setProperty("address", ip);
            else {
                throw new InvalidParameterException("Property interface is set to " + interfaceName + ", but has no ip");
            }
        }
    }
}
