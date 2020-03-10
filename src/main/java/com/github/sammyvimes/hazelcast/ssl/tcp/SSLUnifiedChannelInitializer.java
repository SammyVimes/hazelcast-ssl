package com.github.sammyvimes.hazelcast.ssl.tcp;


import com.hazelcast.config.SSLConfig;
import com.hazelcast.internal.networking.Channel;
import com.hazelcast.internal.networking.ChannelOption;
import com.hazelcast.nio.tcp.UnifiedProtocolDecoder;
import com.hazelcast.nio.tcp.UnifiedProtocolEncoder;
import com.hazelcast.spi.properties.GroupProperty;
import com.hazelcast.spi.properties.HazelcastProperties;
import com.hazelcast.util.function.Function;

import java.util.concurrent.Executor;

public class SSLUnifiedChannelInitializer extends SSLChannelInitializer {

    private final Function<Channel, Handlers<UnifiedProtocolDecoder, UnifiedProtocolEncoder>> handlerProvider;

    private final HazelcastProperties props;

    public SSLUnifiedChannelInitializer(final SSLConfig sslConfig,
                                        final HazelcastProperties props,
                                        final Executor executor,
                                        final Function<Channel, Handlers<UnifiedProtocolDecoder, UnifiedProtocolEncoder>> handlerProvider) {
        super(sslConfig, executor);
        this.handlerProvider = handlerProvider;
        this.props = props;
    }

    @Override
    protected boolean forClient() {
        return false;
    }

    @Override
    protected void initPipeline(final Channel channel) {
        final Handlers<UnifiedProtocolDecoder, UnifiedProtocolEncoder> pair = this.handlerProvider.apply(channel);
        channel.inboundPipeline().addLast(pair.getInboundHandler());
        channel.outboundPipeline().addLast(pair.getOutboundHandler());
    }

    @Override
    protected void configChannel(final Channel channel) {
        channel.options().setOption(ChannelOption.DIRECT_BUF, this.props.getBoolean(GroupProperty.SOCKET_BUFFER_DIRECT)).setOption(ChannelOption.TCP_NODELAY, this.props.getBoolean(GroupProperty.SOCKET_NO_DELAY)).setOption(ChannelOption.SO_KEEPALIVE, this.props.getBoolean(GroupProperty.SOCKET_KEEP_ALIVE)).setOption(ChannelOption.SO_SNDBUF, this.props.getInteger(GroupProperty.SOCKET_SEND_BUFFER_SIZE) * 1024).setOption(ChannelOption.SO_RCVBUF, this.props.getInteger(GroupProperty.SOCKET_RECEIVE_BUFFER_SIZE) * 1024).setOption(ChannelOption.SO_LINGER, this.props.getSeconds(GroupProperty.SOCKET_LINGER_SECONDS));
    }
}

