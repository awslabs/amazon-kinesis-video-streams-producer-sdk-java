package com.amazonaws.kinesisvideo.service;

import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of an {@link InputStream} which blocks read operations until triggered
 */
public class BlockingInputStream extends InputStream {
    private final Object monitor;
    private final InputStream inputStream;
    private final Log log;
    private boolean unblocked;

    public BlockingInputStream(@Nonnull final InputStream inputStream,
                               @Nonnull final Log log) {
        this.inputStream = Preconditions.checkNotNull(inputStream);
        this.log = Preconditions.checkNotNull(log);
        this.unblocked = false;
        this.monitor = new Object();
    }

    @Override
    public int read() throws IOException {
        // Await until unblocked
        await();
        return inputStream.read();
    }

    @Override
    public int read(byte[] b,
                    int off,
                    int len)
            throws IOException {
        await();
        return inputStream.read(b, off, len);
    }

    @Override
    public int read(byte[] b)
            throws IOException {
        await();
        return inputStream.read(b);
    }

    @Override
    public void close()
            throws IOException {
        unblock();
        inputStream.close();
    }

    public void unblock() {
        synchronized (monitor) {
            unblocked = true;
            log.debug("Stream unblocked notification.");
            monitor.notify();
        }
    }

    private void await() {
        synchronized (monitor) {
            while (!unblocked) {
                try {
                    monitor.wait();
                } catch (final InterruptedException e) {
                    log.exception(e, "Waiting for the data stream to become unblocked threw an interrupted exception. Continuing...");
                }
            }
        }
    }
}
