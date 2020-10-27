package channels;

import babel.generic.ProtoMessage;
import babel.initializers.ChannelInitializer;
import channel.ChannelListener;
import network.ISerializer;

import java.io.IOException;
import java.util.Properties;

public class MultiLoggerChannelInitializer implements ChannelInitializer<MultiLoggerChannel> {
    @Override
    public MultiLoggerChannel initialize(ISerializer<ProtoMessage> serializer, ChannelListener<ProtoMessage> list, Properties properties, short protoId) throws IOException {
        return MultiLoggerChannel.getInstance(serializer, list, protoId, properties);
    }
}
