package com.github.sammyvimes.hazelcast.ssl.tcp;

import javax.net.ssl.SSLEngine;
import java.util.Properties;

public interface SSLEngineFactory {
    void init(Properties properties, boolean forClient) throws Exception;

    SSLEngine create(boolean clientMode);
}
