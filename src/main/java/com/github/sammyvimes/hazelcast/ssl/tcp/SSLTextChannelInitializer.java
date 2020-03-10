package com.github.sammyvimes.hazelcast.ssl.tcp;

import com.hazelcast.config.EndpointConfig;
import com.hazelcast.instance.ProtocolType;
import com.hazelcast.internal.networking.Channel;
import com.hazelcast.nio.IOService;
import com.hazelcast.nio.ascii.MemcacheTextDecoder;
import com.hazelcast.nio.ascii.RestApiTextDecoder;
import com.hazelcast.nio.ascii.TextDecoder;
import com.hazelcast.nio.ascii.TextEncoder;
import com.hazelcast.nio.tcp.TcpIpConnection;
import com.hazelcast.nio.tcp.TextHandshakeDecoder;

import java.util.concurrent.Executor;

public class SSLTextChannelInitializer extends MultiSocketSSLChannelInitializer {
    private final boolean rest;

    public SSLTextChannelInitializer(final EndpointConfig endpointConfig, final Executor tlsExecutor, final IOService ioService, final boolean rest) {
        super(endpointConfig, tlsExecutor, ioService);
        this.rest = rest;
    }

    protected void initPipeline(final Channel channel) {
        final TcpIpConnection connection = (TcpIpConnection) channel.attributeMap().get(TcpIpConnection.class);
        final TextEncoder encoder = new TextEncoder(connection);
        final TextDecoder decoder = this.rest ? new RestApiTextDecoder(connection, encoder, true) : new MemcacheTextDecoder(connection, encoder, true);
        channel.outboundPipeline().addLast(encoder);
        channel.inboundPipeline().addLast(new TextHandshakeDecoder(this.rest ? ProtocolType.REST : ProtocolType.MEMCACHE, decoder));
    }
}
