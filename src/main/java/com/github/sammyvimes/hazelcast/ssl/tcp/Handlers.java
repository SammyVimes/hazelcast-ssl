package com.github.sammyvimes.hazelcast.ssl.tcp;

import com.hazelcast.internal.networking.InboundHandler;
import com.hazelcast.internal.networking.OutboundHandler;

public class Handlers<I extends InboundHandler, O extends OutboundHandler> {
    private final I inboundHandler;
    private final O outboundHandler;

    public Handlers(final I inboundHandler, final O outboundHandler) {
        this.inboundHandler = inboundHandler;
        this.outboundHandler = outboundHandler;
    }

    I getInboundHandler() {
        return this.inboundHandler;
    }

    O getOutboundHandler() {
        return this.outboundHandler;
    }
}
