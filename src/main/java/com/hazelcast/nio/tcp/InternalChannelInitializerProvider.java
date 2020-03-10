package com.hazelcast.nio.tcp;

import com.hazelcast.config.EndpointConfig;
import com.hazelcast.internal.networking.ChannelInitializer;
import com.hazelcast.nio.IOService;

/**
 * The only way to create default ChannelInitializer's in class from different package
 * is to have them created in com.hazelcast.nio.tcp package :(
 */
public class InternalChannelInitializerProvider {

    public static ChannelInitializer provideMemberChannelInitializer(IOService ioService, EndpointConfig endpointConfig) {
        return new MemberChannelInitializer(ioService, endpointConfig);
    }


    public static ChannelInitializer provideClientChannelInitializer(IOService ioService, EndpointConfig endpointConfig) {
        return new ClientChannelInitializer(ioService, endpointConfig);
    }

}
