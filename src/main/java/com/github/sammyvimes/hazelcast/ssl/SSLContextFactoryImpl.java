package com.github.sammyvimes.hazelcast.ssl;

import com.hazelcast.nio.ssl.SSLContextFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Properties;

import static com.hazelcast.nio.IOUtil.closeResource;

public class SSLContextFactoryImpl implements SSLContextFactory {

    private static final String JAVA_NET_SSL_PREFIX = "javax.net.ssl.";

    public static class Props {
        public static final String KEY_STORE_PASSWORD = "keyStorePassword";
        public static final String KEY_STORE = "keyStore";
        public static final String KEY_MANAGER_ALGORITHM = "keyManagerAlgorithm";
        public static final String KEY_STORE_TYPE = "keyStoreType";
        public static final String TRUST_STORE = "trustStore";
        public static final String TRUST_STORE_PASSWORD = "trustStorePassword";
        public static final String TRUST_MANAGER_ALGORITHM = "trustManagerAlgorithm";
        public static final String TRUST_STORE_TYPE = "trustStoreType";
        public static final String PROTOCOL = "protocol";
        public static final String MUTUAL_AUTHENTICATION = "mutualAuthentication";
        public static final String CIPHERSUITES = "ciphersuites";
    }

    private KeyManagerFactory kmf;
    private TrustManagerFactory tmf;
    private String protocol;
    private SSLContext sslContext;

    public static TrustManagerFactory loadTrustManagerFactory(final String trustStorePassword,
                                                              final String trustStore,
                                                              final String trustManagerAlgorithm) throws Exception {
        return loadTrustManagerFactory(trustStorePassword, trustStore, trustManagerAlgorithm, "JKS");
    }

    private static TrustManagerFactory loadTrustManagerFactory(final String trustStorePassword,
                                                               final String trustStore,
                                                               final String trustManagerAlgorithm,
                                                               final String trustStoreType) throws Exception {
        if (trustStore == null) {
            return null;
        }

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(trustManagerAlgorithm);
        final char[] passPhrase = trustStorePassword == null ? null : trustStorePassword.toCharArray();
        final KeyStore ts = KeyStore.getInstance(trustStoreType);
        loadKeyStore(ts, passPhrase, trustStore);
        tmf.init(ts);
        return tmf;
    }

    public static KeyManagerFactory loadKeyManagerFactory(final String keyStorePassword,
                                                          final String keyStore,
                                                          final String keyManagerAlgorithm) throws Exception {
        return loadKeyManagerFactory(keyStorePassword, keyStore, keyManagerAlgorithm, "JKS");
    }

    private static KeyManagerFactory loadKeyManagerFactory(final String keyStorePassword,
                                                           final String keyStore,
                                                           final String keyManagerAlgorithm,
                                                           final String keyStoreType) throws Exception {
        if (keyStore == null) {
            return null;
        }

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(keyManagerAlgorithm);
        final char[] passPhrase = keyStorePassword == null ? null : keyStorePassword.toCharArray();
        final KeyStore ks = KeyStore.getInstance(keyStoreType);
        loadKeyStore(ks, passPhrase, keyStore);
        kmf.init(ks, passPhrase);
        return kmf;
    }

    private static void loadKeyStore(final KeyStore ks, final char[] passPhrase, final String keyStoreFile)
            throws IOException, NoSuchAlgorithmException, CertificateException {
        final InputStream in = new FileInputStream(keyStoreFile);
        try {
            ks.load(in, passPhrase);
        } finally {
            closeResource(in);
        }
    }

    public static String getProperty(final Properties properties, final String property) {
        String value = properties.getProperty(property);
        if (value == null) {
            value = properties.getProperty(JAVA_NET_SSL_PREFIX + property);
        }
        if (value == null) {
            value = System.getProperty(JAVA_NET_SSL_PREFIX + property);
        }
        return value;
    }

    private static String getProperty(final Properties properties, final String property, final String defaultValue) {
        final String value = getProperty(properties, property);
        return value != null ? value : defaultValue;
    }

    @Override
    public void init(final Properties properties) throws Exception {
        load(properties);
        final KeyManager[] keyManagers = kmf == null ? null : kmf.getKeyManagers();
        final TrustManager[] trustManagers = tmf == null ? null : tmf.getTrustManagers();

        sslContext = SSLContext.getInstance(protocol);
        sslContext.init(keyManagers, trustManagers, null);
    }

    @Override
    public SSLContext getSSLContext() {
        return sslContext;
    }

    private void load(final Properties properties) throws Exception {
        final String keyStorePassword = getProperty(properties, Props.KEY_STORE_PASSWORD);
        final String keyStore = getProperty(properties, Props.KEY_STORE);
        final String keyManagerAlgorithm = getProperty(properties, Props.KEY_MANAGER_ALGORITHM, KeyManagerFactory.getDefaultAlgorithm());
        final String keyStoreType = getProperty(properties, Props.KEY_STORE_TYPE, "JKS");

        final String trustStore = getProperty(properties, Props.TRUST_STORE, keyStore);
        final String trustStorePassword = getProperty(properties, Props.TRUST_STORE_PASSWORD, keyStorePassword);
        final String trustManagerAlgorithm
                = getProperty(properties, Props.TRUST_MANAGER_ALGORITHM, TrustManagerFactory.getDefaultAlgorithm());
        final String trustStoreType = getProperty(properties, Props.TRUST_STORE_TYPE, "JKS");

        this.protocol = getProperty(properties, Props.PROTOCOL, "TLS");
        this.kmf = loadKeyManagerFactory(keyStorePassword, keyStore, keyManagerAlgorithm, keyStoreType);
        this.tmf = loadTrustManagerFactory(trustStorePassword, trustStore, trustManagerAlgorithm, trustStoreType);
    }
    
    
}
