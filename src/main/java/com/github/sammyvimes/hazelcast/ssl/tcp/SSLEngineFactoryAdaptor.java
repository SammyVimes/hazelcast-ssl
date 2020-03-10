package com.github.sammyvimes.hazelcast.ssl.tcp;

import com.github.sammyvimes.hazelcast.ssl.SSLContextFactoryImpl;
import com.hazelcast.config.ConfigurationException;
import com.hazelcast.internal.util.JavaVersion;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.nio.ssl.SSLContextFactory;
import com.hazelcast.util.StringUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class SSLEngineFactoryAdaptor implements SSLEngineFactory {
    private static final Object LOCK = new Object();
    private final ILogger logger = Logger.getLogger(SSLEngineFactoryAdaptor.class);
    private final SSLContextFactory sslContextFactory;
    private volatile String[] cipherSuites;
    private volatile String protocol;

    public SSLEngineFactoryAdaptor(final SSLContextFactory sslContextFactory) {
        this.sslContextFactory = sslContextFactory;
    }

    public SSLEngine create(final boolean clientMode) {
        final SSLEngine sslEngine = this.createSSLEngine();
        sslEngine.setUseClientMode(clientMode);
        sslEngine.setEnableSessionCreation(true);
        if (this.cipherSuites != null) {
            sslEngine.setEnabledCipherSuites(this.cipherSuites);
        }

        if (this.protocol != null) {
            final String[] enabledProtocols = this.findEnabledProtocols(this.protocol, sslEngine.getSupportedProtocols());
            if (enabledProtocols.length > 0) {
                sslEngine.setEnabledProtocols(enabledProtocols);
            } else {
                this.logger.warning("Enabling SSL protocol failed. Check if configured value contains a supported value" + Arrays.toString(sslEngine.getSupportedProtocols()));
            }
        }

        return sslEngine;
    }

    public void init(final Properties properties, final boolean forClient) throws Exception {
        this.sslContextFactory.init(properties);
        final String[] configuredCipherSuites = StringUtil.splitByComma(SSLContextFactoryImpl.getProperty(properties, SSLContextFactoryImpl.Props.CIPHERSUITES), false);
        if (configuredCipherSuites != null) {
            final SSLEngine sslEngine = this.createSSLEngine();
            final String[] supportedCipherSuites = sslEngine.getSupportedCipherSuites();
            this.cipherSuites = StringUtil.intersection(configuredCipherSuites, supportedCipherSuites);
            if (this.cipherSuites.length < 1) {
                throw new ConfigurationException("No configured SSL cipher suite name is valid. Check if configured values " + Arrays.toString(configuredCipherSuites) + " contain supported values: " + Arrays.toString(supportedCipherSuites));
            }
        }

        this.protocol = SSLContextFactoryImpl.getProperty(properties, SSLContextFactoryImpl.Props.PROTOCOL);
    }

    private String[] findEnabledProtocols(final String configuredName, final String[] supportedProtocols) {
        final List<String> enabled = new ArrayList<>();

        for (final String protocol : supportedProtocols) {
            if (configuredName.equals(protocol)
                    || ("TLS".equals(configuredName) && protocol.matches("TLSv1(\\.\\d+)?"))
                    || ("SSL".equals(configuredName) && protocol.equals("SSLv3"))) {
                enabled.add(protocol);
            }
        }

        return enabled.toArray(new String[0]);
    }

    private SSLEngine createSSLEngine() {
        final SSLContext sslContext = this.sslContextFactory.getSSLContext();
        if (JavaVersion.isAtMost(JavaVersion.JAVA_1_6)) {
            synchronized (LOCK) {
                return sslContext.createSSLEngine();
            }
        } else {
            return sslContext.createSSLEngine();
        }
    }
}
