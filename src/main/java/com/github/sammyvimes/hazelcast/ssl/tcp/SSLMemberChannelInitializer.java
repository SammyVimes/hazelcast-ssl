package com.github.sammyvimes.hazelcast.ssl.tcp;


import com.hazelcast.config.EndpointConfig;
import com.hazelcast.instance.EndpointQualifier;
import com.hazelcast.instance.ProtocolType;
import com.hazelcast.internal.networking.Channel;
import com.hazelcast.internal.networking.InboundHandler;
import com.hazelcast.internal.networking.OutboundHandler;
import com.hazelcast.nio.IOService;
import com.hazelcast.nio.tcp.MemberProtocolEncoder;
import com.hazelcast.nio.tcp.SingleProtocolDecoder;
import com.hazelcast.nio.tcp.TcpIpConnection;

import java.util.concurrent.Executor;

public class SSLMemberChannelInitializer extends MultiSocketSSLChannelInitializer {
    public SSLMemberChannelInitializer(final EndpointConfig endpointConfig, final Executor tlsExecutor, final IOService ioService) {
        super(endpointConfig, tlsExecutor, ioService);
    }

    protected void initPipeline(final Channel channel) {
        final TcpIpConnection connection = (TcpIpConnection) channel.attributeMap().get(TcpIpConnection.class);
        final OutboundHandler[] outboundHandlers = this.ioService.createOutboundHandlers(EndpointQualifier.MEMBER, connection);
        final InboundHandler[] inboundHandlers = this.ioService.createInboundHandlers(EndpointQualifier.MEMBER, connection);
        final MemberProtocolEncoder protocolEncoder = new MemberProtocolEncoder(outboundHandlers);
        final SingleProtocolDecoder protocolDecoder = new SingleProtocolDecoder(ProtocolType.MEMBER, inboundHandlers, protocolEncoder);
        channel.outboundPipeline().addLast(protocolEncoder);
        channel.inboundPipeline().addLast(protocolDecoder);
    }
}
