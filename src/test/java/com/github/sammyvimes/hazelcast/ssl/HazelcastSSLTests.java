package com.github.sammyvimes.hazelcast.ssl;


import com.github.sammyvimes.hazelcast.ssl.node.SSLNodeContext;
import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.instance.HazelcastInstanceFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RunWith(JUnit4.class)
@SuppressWarnings("sunapi")
public class HazelcastSSLTests {

    int port = 5001;

    final List<Integer> ports = new ArrayList<>();

    @Test
    public void foo() throws Exception {
        final Config config = new Config();
        final String file = createJKS();
        final HazelcastInstance hazelcastInstance = getHazelcastInstance(config, file);

        final List<HazelcastInstance> instances = IntStream.range(0, 5).mapToObj(value -> getHazelcastInstance(config, file)).collect(Collectors.toList());


        final IMap<Object, Object> map = hazelcastInstance.getMap("some-map");
        map.put("testKey", "testVal");

        instances.forEach(anotherInstance -> {
            final IMap<Object, Object> mapFromAnotherInstance = anotherInstance.getMap("some-map");
            final Object valueByKey = mapFromAnotherInstance.get("testKey");
            Assert.assertEquals("testVal", valueByKey);
        });
    }

    private HazelcastInstance getHazelcastInstance(final Config config, final String file) {
        final AdvancedNetworkConfig networkConfig = config.getAdvancedNetworkConfig();

        networkConfig.setEnabled(true);

        final SSLConfig sslConfig = new SSLConfig();
        sslConfig.setEnabled(true);
        sslConfig.setFactoryImplementation(new SSLContextFactoryImpl());

        final String keyStorePath = file;
        sslConfig.setProperty(SSLContextFactoryImpl.Props.KEY_STORE, keyStorePath);
        sslConfig.setProperty(SSLContextFactoryImpl.Props.KEY_STORE_PASSWORD, "password");
        sslConfig.setProperty(SSLContextFactoryImpl.Props.MUTUAL_AUTHENTICATION, "REQUIRED");

        final int thePort = this.port++;

        final ServerSocketEndpointConfig serverSocketEndpointConfig = new ServerSocketEndpointConfig();
        serverSocketEndpointConfig.setPort(thePort);
        serverSocketEndpointConfig.setSSLConfig(sslConfig);

        if (this.port == 5001) {
            networkConfig.setClientEndpointConfig(serverSocketEndpointConfig);
        } else {
            networkConfig.setMemberEndpointConfig(serverSocketEndpointConfig);
        }

        ports.add(thePort);

        final JoinConfig join = networkConfig.getJoin();
        final TcpIpConfig tcpIpConfig = join.getTcpIpConfig();
        tcpIpConfig.setEnabled(true);

        join.getMulticastConfig().setEnabled(false);

        final List<String> members = ports.stream().map(integer -> "localhost:" + integer).collect(Collectors.toList());
        tcpIpConfig.setMembers(members);

        return HazelcastInstanceFactory.newHazelcastInstance(config,
                config.getInstanceName(),
                new SSLNodeContext());
    }

    private String createJKS() throws GeneralSecurityException, IOException, URISyntaxException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        char[] password = "password".toCharArray();
        ks.load(null, password);

        // Store away the keystore.
        final Path path = Paths.get(ClassLoader.getSystemResource(".").getPath(), "newKeyStoreFileName.jks");
        path.getParent().toFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(path.toFile());

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(4096);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        Certificate[] chain = {generateCertificate("cn=Unknown", keyPair, 365, "SHA256withRSA")};

        ks.setKeyEntry("main", keyPair.getPrivate(), password, chain);
        ks.store(fos, password);
        fos.close();
        return path.toString();
    }

    private X509Certificate generateCertificate(String dn, KeyPair keyPair, int validity, String sigAlgName) throws GeneralSecurityException, IOException {
        PrivateKey privateKey = keyPair.getPrivate();

        X509CertInfo info = new X509CertInfo();

        Date from = new Date();
        Date to = new Date(from.getTime() + validity * 1000L * 24L * 60L * 60L);

        CertificateValidity interval = new CertificateValidity(from, to);
        BigInteger serialNumber = new BigInteger(64, new SecureRandom());
        X500Name owner = new X500Name(dn);
        AlgorithmId sigAlgId = new AlgorithmId(AlgorithmId.md5WithRSAEncryption_oid);

        info.set(X509CertInfo.VALIDITY, interval);
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(serialNumber));
        info.set(X509CertInfo.SUBJECT, owner);
        info.set(X509CertInfo.ISSUER, owner);
        info.set(X509CertInfo.KEY, new CertificateX509Key(keyPair.getPublic()));
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(sigAlgId));

        // Sign the cert to identify the algorithm that's used.
        X509CertImpl certificate = new X509CertImpl(info);
        certificate.sign(privateKey, sigAlgName);

        // Update the algorith, and resign.
        sigAlgId = (AlgorithmId) certificate.get(X509CertImpl.SIG_ALG);
        info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, sigAlgId);
        certificate = new X509CertImpl(info);
        certificate.sign(privateKey, sigAlgName);

        return certificate;
    }

}
