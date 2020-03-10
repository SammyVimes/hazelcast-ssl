package com.github.sammyvimes.hazelcast.ssl.tcp;

import com.hazelcast.internal.networking.Channel;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.util.Preconditions;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

class SSLExecutor {
    private final Executor executor;

    SSLExecutor(final Executor executor) {
        this.executor = Preconditions.checkNotNull(executor, "executor can't be null");
    }

    void executeHandshakeTasks(final SSLEngine sslEngine, final Channel channel) {
        final List<Runnable> tasks = this.collectTasks(sslEngine);
        final AtomicInteger remaining = new AtomicInteger(tasks.size());

        for (final Runnable task : tasks) {
            this.executor.execute(new HandshakeTask(task, remaining, sslEngine, channel));
        }

    }

    void execute(final Runnable task) {
        this.executor.execute(task);
    }

    private List<Runnable> collectTasks(final SSLEngine sslEngine) {
        final List<Runnable> tasks = new LinkedList<>();

        Runnable task;
        while ((task = sslEngine.getDelegatedTask()) != null) {
            tasks.add(task);
        }

        return tasks;
    }

    private static class HandshakeTask implements Runnable {
        private final Runnable task;
        private final AtomicInteger remaining;
        private final SSLEngine sslEngine;
        private final Channel channel;

        HandshakeTask(final Runnable task, final AtomicInteger remaining, final SSLEngine sslEngine, final Channel channel) {
            this.task = task;
            this.remaining = remaining;
            this.sslEngine = sslEngine;
            this.channel = channel;
        }

        public void run() {
            try {
                this.task.run();
            } catch (final Exception exception) {
                final ILogger logger = Logger.getLogger(SSLExecutor.HandshakeTask.class);
                logger.warning("Failed to execute handshake task for " + this.channel, exception);
            } finally {
                this.onTaskCompletion();
            }

        }

        private void onTaskCompletion() {
            if (this.remaining.decrementAndGet() == 0) {
                if (this.sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
                    this.channel.outboundPipeline().wakeup();
                } else {
                    this.channel.inboundPipeline().wakeup();
                }
            }

        }
    }
}
