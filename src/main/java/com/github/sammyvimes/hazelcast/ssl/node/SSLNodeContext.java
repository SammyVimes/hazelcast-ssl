package com.github.sammyvimes.hazelcast.ssl.node;

import com.hazelcast.instance.DefaultNodeContext;
import com.hazelcast.instance.Node;
import com.hazelcast.instance.NodeExtension;

public class SSLNodeContext extends DefaultNodeContext {

    @Override
    public NodeExtension createNodeExtension(final Node node) {
        return new SSLNodeExtension(node);
    }

}
