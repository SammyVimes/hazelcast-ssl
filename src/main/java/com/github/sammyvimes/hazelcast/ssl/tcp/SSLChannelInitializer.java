package com.github.sammyvimes.hazelcast.ssl.tcp;

import com.github.sammyvimes.hazelcast.ssl.SSLContextFactoryImpl;
import com.hazelcast.config.ConfigurationException;
import com.hazelcast.config.SSLConfig;
import com.hazelcast.core.HazelcastException;
import com.hazelcast.internal.networking.Channel;
import com.hazelcast.internal.networking.ChannelInitializer;
import com.hazelcast.nio.ssl.BasicSSLContextFactory;
import com.hazelcast.nio.ssl.SSLContextFactory;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;

public abstract class SSLChannelInitializer implements ChannelInitializer {
    private final SSLConfig sslConfig;
    private final SSLEngineFactory sslEngineFactory;
    private final String mutualAuthentication;
    private final SSLExecutor sslExecutor;

    public SSLChannelInitializer(final SSLConfig sslConfig, final Executor sslExecutor) {
        this.sslConfig = sslConfig;
        this.sslEngineFactory = this.loadSSLEngineFactory();
        this.sslExecutor = new SSLExecutor(sslExecutor);
        this.mutualAuthentication = SSLContextFactoryImpl.getProperty(sslConfig.getProperties(), SSLContextFactoryImpl.Props.MUTUAL_AUTHENTICATION);
    }

    private SSLEngineFactory loadSSLEngineFactory() {
        Object implementation = this.sslConfig.getFactoryImplementation();

        try {
            final String factoryClassName = this.sslConfig.getFactoryClassName();
            if (implementation == null && factoryClassName != null) {
                implementation = Class.forName(factoryClassName).newInstance();
            }

            if (implementation == null) {
                implementation = new BasicSSLContextFactory();
            }

            if (implementation instanceof SSLContextFactory) {
                implementation = new SSLEngineFactoryAdaptor((SSLContextFactory) implementation);
            }

            final SSLEngineFactory sslEngineFactory = (SSLEngineFactory) implementation;
            sslEngineFactory.init(this.sslConfig.getProperties(), this.forClient());
            return sslEngineFactory;
        } catch (final HazelcastException exception) {
            throw exception;
        } catch (final NoSuchAlgorithmException | IOException exception) {
            throw new ConfigurationException("Error while loading SSL engine for: " + this.getClass().getSimpleName(), exception);
        } catch (final Exception exception) {
            throw new HazelcastException(exception);
        }
    }

    protected abstract boolean forClient();

    @Override
    public final void initChannel(final Channel channel) throws Exception {
        this.configChannel(channel);
        final SSLEngine sslEngine = this.sslEngineFactory.create(channel.isClientMode());
        if ("REQUIRED".equals(this.mutualAuthentication)) {
            sslEngine.setNeedClientAuth(true);
        } else if ("OPTIONAL".equals(this.mutualAuthentication)) {
            sslEngine.setWantClientAuth(true);
        }

        sslEngine.beginHandshake();
        channel.inboundPipeline().addLast(new SSLInboundHandler(sslEngine, this.sslExecutor));
        this.initPipeline(channel);
        channel.outboundPipeline().addLast(new SSLOutboundHandler(sslEngine, this.sslExecutor));
    }

    protected abstract void initPipeline(Channel channel);

    protected abstract void configChannel(Channel channel);
}
