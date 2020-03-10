package com.github.sammyvimes.hazelcast.ssl.tcp;

import com.hazelcast.config.EndpointConfig;
import com.hazelcast.internal.networking.Channel;
import com.hazelcast.nio.IOService;
import com.hazelcast.nio.IOUtil;

import java.util.concurrent.Executor;

public abstract class MultiSocketSSLChannelInitializer extends SSLChannelInitializer {
    protected final EndpointConfig config;
    protected final IOService ioService;

    public MultiSocketSSLChannelInitializer(final EndpointConfig endpointConfig, final Executor sslExecutor, final IOService ioService) {
        super(endpointConfig.getSSLConfig(), sslExecutor);
        this.config = endpointConfig;
        this.ioService = ioService;
    }

    protected boolean forClient() {
        return false;
    }

    protected void configChannel(final Channel channel) {
        IOUtil.setChannelOptions(channel, this.config);
    }
}
