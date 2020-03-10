package com.github.sammyvimes.hazelcast.ssl.tcp;

import com.hazelcast.internal.networking.ChannelOption;
import com.hazelcast.internal.networking.HandlerStatus;
import com.hazelcast.internal.networking.InboundHandler;
import com.hazelcast.nio.IOUtil;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.io.EOFException;
import java.nio.ByteBuffer;

public class SSLInboundHandler extends InboundHandler<ByteBuffer, ByteBuffer> {
    private final SSLEngine sslEngine;
    private final SSLExecutor SSLExecutor;

    private ByteBuffer appBuffer;
    private SSLSession sslSession;

    private boolean handshaking = true;

    public SSLInboundHandler(final SSLEngine sslEngine, final SSLExecutor SSLExecutor) {
        this.sslEngine = sslEngine;
        this.SSLExecutor = SSLExecutor;
        this.appBuffer = IOUtil.newByteBuffer(sslEngine.getSession().getApplicationBufferSize(), false);
    }

    static SSLException newSSLException(final Throwable t) {
        return new SSLException("Remote socket closed during SSL/TLS handshake.  This is probably caused by a SSL/TLS authentication problem resulting in the remote side closing the socket.", t);
    }

    public void handlerAdded() {
        this.initSrcBuffer(this.sslEngine.getSession().getPacketBufferSize());
    }

    public void interceptError(final Throwable t) throws Throwable {
        if (t instanceof EOFException) {
            throw newSSLException(t);
        }
    }

    public HandlerStatus onRead() throws Exception {
        if (!handshaking && !this.drainAppBuffer()) {
            return HandlerStatus.DIRTY;
        } else {
            this.src.flip();

            final boolean wasHandshaking = handshaking;
            try {
                while (true) {
                    final HandlerStatus status;
                    if (handshaking) {
                        status = handleHandshaking();
                    } else {
                        status = handleRegular();
                    }
                    if (status != null) {
                        return status;
                    }
                }
            } finally {
                if (wasHandshaking == handshaking) {
                    IOUtil.compactOrClear(this.src);
                }
            }
        }
    }

    private HandlerStatus handleHandshaking() throws SSLException {
        final HandshakeStatus handshakeStatus = this.sslEngine.getHandshakeStatus();

        switch (handshakeStatus) {
            case FINISHED:
                break;
            case NEED_TASK:
                this.SSLExecutor.executeHandshakeTasks(this.sslEngine, this.channel);
                return HandlerStatus.BLOCKED;
            case NEED_WRAP:
                this.channel.outboundPipeline().wakeup();
                return HandlerStatus.BLOCKED;
            case NEED_UNWRAP:
                final SSLEngineResult unwrapResult = this.sslEngine.unwrap(this.src, this.appBuffer);
                final Status unwrapStatus = unwrapResult.getStatus();
                if (unwrapStatus == Status.OK) {
                    break;
                }

                if (unwrapStatus == Status.CLOSED) {
                    return HandlerStatus.CLEAN;
                }

                if (unwrapStatus != Status.BUFFER_UNDERFLOW) {
                    throw new IllegalStateException("Unexpected " + unwrapResult);
                }

                if (this.sslEngine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
                    break;
                }

                return HandlerStatus.CLEAN;
            case NOT_HANDSHAKING:
                if (this.appBuffer.position() != 0) {
                    throw new IllegalStateException("Unexpected data in the appBuffer, it should be empty " + IOUtil.toDebugString("appBuffer", this.appBuffer));
                }

                this.onHandshakeFinished();
                return HandlerStatus.DIRTY;
            default:
                throw new IllegalStateException();
        }
        return null;
    }

    private void onHandshakeFinished() {
        this.handshaking = false;
        this.sslSession = this.sslEngine.getSession();

        // create new buffers
        final int socketReceiveBuffer = this.channel.options().getOption(ChannelOption.SO_RCVBUF);
        final int packetBufferSize = this.sslEngine.getSession().getPacketBufferSize();
        final ByteBuffer oldSrc = this.src;
        this.initSrcBuffer(Math.max(socketReceiveBuffer, packetBufferSize));
        this.appBuffer = IOUtil.newByteBuffer(this.sslSession.getApplicationBufferSize(), this.channel.options().getOption(ChannelOption.DIRECT_BUF));

        // hacky way to make pipeline use new buffer
        this.channel.inboundPipeline().replace(this, this);

        this.src.put(oldSrc);

        this.channel.outboundPipeline().wakeup();
    }

    private HandlerStatus handleRegular() throws SSLException {

        final SSLEngineResult unwrapResult;
        try {
            unwrapResult = this.sslEngine.unwrap(this.src, this.appBuffer);
        } catch (final SSLException exception) {
            throw new SSLException(IOUtil.toDebugString("src", this.src) + " " + IOUtil.toDebugString("app", this.appBuffer) + " " + IOUtil.toDebugString("dst", this.dst), exception);
        }

        switch (unwrapResult.getStatus()) {
            case BUFFER_OVERFLOW:
                if (this.appBuffer.capacity() >= this.sslSession.getApplicationBufferSize()) {
                    return HandlerStatus.DIRTY;
                }

                this.appBuffer = this.newAppBuffer();
                break;
            case BUFFER_UNDERFLOW:
                return HandlerStatus.CLEAN;
            case OK:
                if (!this.drainAppBuffer()) {
                    return HandlerStatus.DIRTY;
                }

                if (this.src.remaining() == 0) {
                    return HandlerStatus.CLEAN;
                }
                break;
            case CLOSED:
                return HandlerStatus.CLEAN;
            default:
                throw new IllegalStateException();
        }
        return null;
    }

    private ByteBuffer newAppBuffer() {
        return IOUtil.newByteBuffer(this.sslSession.getApplicationBufferSize(), this.channel.options().getOption(ChannelOption.DIRECT_BUF));
    }

    private boolean drainAppBuffer() {
        this.appBuffer.flip();
        final int available = this.appBuffer.remaining();
        if (this.dst.remaining() < available) {
            final int oldLimit = this.appBuffer.limit();
            this.appBuffer.limit(this.appBuffer.position() + this.dst.remaining());
            this.dst.put(this.appBuffer);
            this.appBuffer.limit(oldLimit);
        } else {
            this.dst.put(this.appBuffer);
        }

        if (this.appBuffer.hasRemaining()) {
            IOUtil.compactOrClear(this.appBuffer);
            return false;
        } else {
            this.appBuffer.clear();
            return true;
        }
    }

}
