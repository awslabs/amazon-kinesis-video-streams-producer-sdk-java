package com.amazonaws.kinesisvideo.stream.throttling;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream that is bandwidth-throttled by a {@link BandwidthThrottler}. It redirects all write calls to the
 * throttler.
 */
public class BandwidthThrottledOutputStream extends OutputStream {
    private final OutputStream outputStream;
    private final BandwidthThrottler throttler;

    // This is so that we don't have to allocate it all the time. Just one byte!
    private final byte[] oneByteBuffer = new byte[1];

    public BandwidthThrottledOutputStream(final OutputStream outputStream,
                                          final BandwidthThrottler throttler) {
        this.outputStream = outputStream;
        this.throttler = throttler;
    }

    @Override
    public void write(final int b) throws IOException {
        oneByteBuffer[0] = (byte) b;
        write(oneByteBuffer, 0, 1);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        int bytesWritten = 0;
        while (bytesWritten < len) {
            final int allowedBytesToWrite = throttler.getAllowedBytes(len - bytesWritten);
            outputStream.write(b, off + bytesWritten, allowedBytesToWrite);
            bytesWritten += allowedBytesToWrite; // TODO: actually use the returned value of write()
        }
    }

    @Override
    public void flush() throws IOException {
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }
}
