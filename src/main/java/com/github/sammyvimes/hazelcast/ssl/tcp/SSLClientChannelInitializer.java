package com.github.sammyvimes.hazelcast.ssl.tcp;

import com.hazelcast.client.impl.protocol.util.ClientMessageDecoder;
import com.hazelcast.client.impl.protocol.util.ClientMessageEncoder;
import com.hazelcast.config.EndpointConfig;
import com.hazelcast.instance.ProtocolType;
import com.hazelcast.internal.networking.Channel;
import com.hazelcast.nio.IOService;
import com.hazelcast.nio.tcp.SingleProtocolDecoder;
import com.hazelcast.nio.tcp.TcpIpConnection;

import java.util.concurrent.Executor;

public class SSLClientChannelInitializer extends MultiSocketSSLChannelInitializer {
    public SSLClientChannelInitializer(final EndpointConfig endpointConfig, final Executor tlsExecutor, final IOService ioService) {
        super(endpointConfig, tlsExecutor, ioService);
    }

    protected void initPipeline(final Channel channel) {
        final TcpIpConnection connection = (TcpIpConnection) channel.attributeMap().get(TcpIpConnection.class);
        final SingleProtocolDecoder protocolDecoder = new SingleProtocolDecoder(ProtocolType.CLIENT, new ClientMessageDecoder(connection, this.ioService.getClientEngine()));
        channel.outboundPipeline().addLast(new ClientMessageEncoder());
        channel.inboundPipeline().addLast(protocolDecoder);
    }
}
