package com.github.sammyvimes.hazelcast.ssl.node;

import com.hazelcast.instance.DefaultNodeExtension;
import com.hazelcast.instance.Node;
import com.hazelcast.internal.networking.ChannelInitializerProvider;
import com.hazelcast.nio.IOService;

public class SSLNodeExtension extends DefaultNodeExtension {
    public SSLNodeExtension(final Node node) {
        super(node);
    }

    @Override
    public ChannelInitializerProvider createChannelInitializerProvider(final IOService ioService) {
        final SSLChannelInitializerProvider provider = new SSLChannelInitializerProvider(ioService, node);
        provider.init();
        return provider;
    }
}
