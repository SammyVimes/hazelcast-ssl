package com.github.sammyvimes.hazelcast.ssl.tcp;

import com.hazelcast.internal.networking.ChannelOption;
import com.hazelcast.internal.networking.HandlerStatus;
import com.hazelcast.internal.networking.OutboundHandler;
import com.hazelcast.nio.IOUtil;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import java.io.EOFException;
import java.nio.ByteBuffer;

public class SSLOutboundHandler extends OutboundHandler<ByteBuffer, ByteBuffer> {
    private final SSLEngine sslEngine;
    private final ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
    private final SSLExecutor sslExecutor;

    private boolean handshaking = true;

    SSLOutboundHandler(final SSLEngine sslEngine, final SSLExecutor sslExecutor) {
        this.sslEngine = sslEngine;
        this.sslExecutor = sslExecutor;
    }

    public void handlerAdded() {
        this.initDstBuffer(this.sslEngine.getSession().getPacketBufferSize());
    }

    public void interceptError(final Throwable t) throws Throwable {
        if (t instanceof EOFException) {
            throw SSLInboundHandler.newSSLException(t);
        }
    }

    public HandlerStatus onWrite() throws Exception {
        IOUtil.compactOrClear(this.dst);

        try {
            final HandlerStatus handlerStatus;
            if (handshaking) {
                handlerStatus = handleHandshake();
            } else {
                handlerStatus = handleRegular();
            }
            return handlerStatus;
        } finally {
            this.dst.flip();
        }
    }

    private HandlerStatus handleHandshake() throws SSLException {
        while (true) {
            final HandshakeStatus handshakeStatus = this.sslEngine.getHandshakeStatus();
            switch (handshakeStatus) {
                case FINISHED:
                    break;
                case NEED_TASK:
                    this.sslExecutor.executeHandshakeTasks(this.sslEngine, this.channel);
                    return HandlerStatus.BLOCKED;
                case NEED_WRAP:
                    final SSLEngineResult wrapResult = this.sslEngine.wrap(this.emptyBuffer, this.dst);
                    final Status wrapResultStatus = wrapResult.getStatus();
                    if (wrapResultStatus == Status.OK) {
                        break;
                    }

                    if (wrapResultStatus == Status.BUFFER_OVERFLOW) {
                        return HandlerStatus.DIRTY;
                    }

                    if (wrapResultStatus == Status.CLOSED) {
                        return HandlerStatus.CLEAN;
                    }

                    throw new IllegalStateException("Unexpected wrapResult:" + wrapResult);
                case NEED_UNWRAP:
                    this.channel.inboundPipeline().wakeup();
                    return HandlerStatus.BLOCKED;
                case NOT_HANDSHAKING:
                    if (!this.isTlsHandshakeBufferDrained()) {
                        return HandlerStatus.DIRTY;
                    }

                    this.onHandshakeFinished();
                    return HandlerStatus.CLEAN;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    private void onHandshakeFinished() {
        this.handshaking = false;

        final int sendBufferSize = this.channel.options().getOption(ChannelOption.SO_SNDBUF);
        final int packetBufferSize = this.sslEngine.getSession().getPacketBufferSize();
        this.initDstBuffer(Math.max(sendBufferSize, packetBufferSize));

        this.channel.outboundPipeline().replace(this, this);

        this.channel.inboundPipeline().wakeup();
    }

    private HandlerStatus handleRegular() throws SSLException {
        final SSLEngineResult wrapResult = this.sslEngine.wrap(this.src, this.dst);
        switch (wrapResult.getStatus()) {
            case BUFFER_OVERFLOW:
                return HandlerStatus.DIRTY;
            case OK:
                if (this.src.remaining() > 0) {
                    return HandlerStatus.DIRTY;
                }

                return HandlerStatus.CLEAN;
            case CLOSED:
                return HandlerStatus.CLEAN;
            default:
                throw new IllegalStateException("Unexpected " + wrapResult);
        }
    }

    private boolean isTlsHandshakeBufferDrained() {
        return this.dst.position() == 0;
    }
}
