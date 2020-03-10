package com.github.sammyvimes.hazelcast.ssl.node;

import com.github.sammyvimes.hazelcast.ssl.tcp.*;
import com.hazelcast.config.*;
import com.hazelcast.instance.EndpointQualifier;
import com.hazelcast.instance.Node;
import com.hazelcast.internal.networking.ChannelInitializer;
import com.hazelcast.internal.networking.ChannelInitializerProvider;
import com.hazelcast.logging.ILogger;
import com.hazelcast.nio.IOService;
import com.hazelcast.nio.ascii.TextChannelInitializer;
import com.hazelcast.nio.tcp.InternalChannelInitializerProvider;
import com.hazelcast.nio.tcp.UnifiedChannelInitializer;
import com.hazelcast.nio.tcp.UnifiedProtocolDecoder;
import com.hazelcast.nio.tcp.UnifiedProtocolEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class SSLChannelInitializerProvider implements ChannelInitializerProvider {

    private final IOService ioService;
    private final ChannelInitializer uniChannelInitializer;
    private final Config config;
    private final Map<EndpointQualifier, ChannelInitializer> initializerMap = new HashMap<>();
    private final ChannelInitializer tlsChannelInitializer;
    private final ILogger logger;
    private final Node node;
    private final boolean unifiedSslEnabled;
    private final Executor sslExecutor;


    public SSLChannelInitializerProvider(final IOService ioService, final Node node) {
        this.ioService = ioService;
        this.uniChannelInitializer = new UnifiedChannelInitializer(ioService);
        this.config = node.getConfig();
        this.node = node;
        this.logger = ioService.getLoggingService()
                .getLogger(SSLChannelInitializerProvider.class);
        this.unifiedSslEnabled = this.unifiedSslEnabled();
        this.sslExecutor = node.nodeEngine.getExecutionService().getGlobalTaskScheduler();
        this.tlsChannelInitializer = this.createUnifiedTlsChannelInitializer();
    }

    @Override
    public ChannelInitializer provide(final EndpointQualifier qualifier) {
        return initializerMap.isEmpty() ? provideUnifiedChannelInitializer() : initializerMap.get(qualifier);
    }

    public void init() {
        final AdvancedNetworkConfig advancedNetworkConfig = config.getAdvancedNetworkConfig();
        if (!advancedNetworkConfig.isEnabled()
                || advancedNetworkConfig.getEndpointConfigs().isEmpty()) {
            return;
        }

        for (final EndpointConfig endpointConfig : advancedNetworkConfig.getEndpointConfigs().values()) {

            switch (endpointConfig.getProtocolType()) {
                case MEMBER:
                    initializerMap.put(EndpointQualifier.MEMBER, provideMemberChannelInitializer(endpointConfig));
                    break;
                case CLIENT:
                    initializerMap.put(EndpointQualifier.CLIENT, provideClientChannelInitializer(endpointConfig));
                    break;
                case REST:
                    initializerMap.put(EndpointQualifier.REST, provideTextChannelInitializer(endpointConfig, true));
                    break;
                case MEMCACHE:
                    initializerMap.put(EndpointQualifier.MEMCACHE, provideTextChannelInitializer(endpointConfig, false));
                    break;
                case WAN:
                    initializerMap.put(endpointConfig.getQualifier(), provideMemberChannelInitializer(endpointConfig));
                    break;
                default:
                    throw new IllegalStateException("Cannot build channel initializer for protocol type "
                            + endpointConfig.getProtocolType());
            }
        }
    }


    private ChannelInitializer provideMemberChannelInitializer(final EndpointConfig endpointConfig) {
        if (this.endpointSslEnabled(endpointConfig)) {
            return new SSLMemberChannelInitializer(endpointConfig, this.sslExecutor, this.ioService);
        } else {
            return InternalChannelInitializerProvider.provideMemberChannelInitializer(ioService, endpointConfig);
        }
    }

    private ChannelInitializer provideClientChannelInitializer(final EndpointConfig endpointConfig) {
        if (this.endpointSslEnabled(endpointConfig)) {
            return new SSLClientChannelInitializer(endpointConfig, this.sslExecutor, this.ioService);
        } else {
            return InternalChannelInitializerProvider.provideClientChannelInitializer(ioService, endpointConfig);
        }
    }

    private ChannelInitializer provideTextChannelInitializer(final EndpointConfig endpointConfig, final boolean rest) {
        if (this.endpointSslEnabled(endpointConfig)) {
            return new SSLTextChannelInitializer(endpointConfig, this.sslExecutor, this.ioService, rest);
        } else {
            return new TextChannelInitializer(ioService, endpointConfig, rest);
        }
    }

    protected ChannelInitializer provideWanChannelInitializer(final EndpointConfig endpointConfig) {
        if (this.endpointSslEnabled(endpointConfig)) {
            return this.tlsChannelInitializer;
        } else {
            throw new UnsupportedOperationException("TODO");
        }
    }

    private ChannelInitializer provideUnifiedChannelInitializer() {
        return this.unifiedSslEnabled ? this.tlsChannelInitializer : uniChannelInitializer;
    }

    private ChannelInitializer createUnifiedTlsChannelInitializer() {
        final NetworkConfig networkConfig = this.node.getConfig().getNetworkConfig();
        final SSLConfig sslConfig = networkConfig.getSSLConfig();
        if (this.unifiedSslEnabled) {
            final SymmetricEncryptionConfig symmetricEncryptionConfig = networkConfig.getSymmetricEncryptionConfig();
            if (symmetricEncryptionConfig != null && symmetricEncryptionConfig.isEnabled()) {
                throw new RuntimeException("SSL and SymmetricEncryption cannot be both enabled!");
            } else {
                this.logger.info("SSL is enabled");

                return new SSLUnifiedChannelInitializer(sslConfig, this.node.getProperties(), this.sslExecutor, (channel) -> {
                    final UnifiedProtocolEncoder encoder = new UnifiedProtocolEncoder(this.ioService);
                    final UnifiedProtocolDecoder decoder = new UnifiedProtocolDecoder(this.ioService, encoder);
                    return new Handlers<>(decoder, encoder);
                });
            }
        } else {
            return null;
        }
    }

    private boolean endpointSslEnabled(final EndpointConfig endpointConfig) {
        return endpointConfig != null && endpointConfig.getSSLConfig() != null && endpointConfig.getSSLConfig()
                .isEnabled();
    }

    private boolean unifiedSslEnabled() {
        final SSLConfig sslConfig = this.node.getConfig().getNetworkConfig().getSSLConfig();
        return sslConfig != null && sslConfig.isEnabled();
    }


}
