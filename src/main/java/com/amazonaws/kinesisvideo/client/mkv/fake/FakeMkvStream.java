package com.amazonaws.kinesisvideo.client.mkv.fake;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import com.google.common.primitives.Longs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public final class FakeMkvStream {
    private static final Random RAND = new Random();
    private static final int DEFAULT_HEADER_SIZE = 300;
    private static final int DEFAULT_CLUSTER_SIZE = 25 * 1024;
    private static final Callback<ByteSource> NO_OP_CALLBACK = new Callback<ByteSource>() {
        @Override
        public void onNext(final ByteSource element) {
            // no op
        }
    };

    private FakeMkvStream() { }

    // All clusters start with the cluster magic number, followed by a
    // size field indicating streaming mode, then the timestamp code and size
    private static final ByteSource CLUSTER_HEADER = ByteSource.wrap(new byte[] {
            // cluster magic number
            (byte) 0x1f, (byte) 0x43, (byte) 0xb6, (byte) 0x75,

            // cluster size (undefined, streaming mode)
            (byte) 0x01, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,

            // timestamp code and size (8 bytes)
            (byte) 0xe7, (byte) 0x88
    });

    public static InputStream asInputStream() throws IOException {
        // 25KB cluster bodies
        return asInputStream(DEFAULT_HEADER_SIZE, DEFAULT_CLUSTER_SIZE, NO_OP_CALLBACK);
    }

    public static InputStream asInputStream(byte[] mkvHeader, int clusterSize, int numberOfClusters,
            Callback<ByteSource> callback) throws IOException {

        Iterable<ByteSource> byteSources = Iterables.concat(
                ImmutableList.of(ByteSource.wrap(mkvHeader)),
                createClusterIterable(clusterSize, numberOfClusters, callback));
        return ByteSource.concat(byteSources).openStream();
    }
    /**
     * Get the fake MKV as an infinite InputStream that repeats the same
     * clusters over and over.
     */
    public static InputStream asInputStream(int headerSize, int clusterSize) throws IOException {
        return asInputStream(headerSize, clusterSize, -1, NO_OP_CALLBACK);
    }

    public static InputStream asInputStream(int headerSize, int clusterSize, Callback<ByteSource> callback)
            throws IOException {
        return asInputStream(headerSize, clusterSize, -1, callback);
    }

    /**
     * Get the fake MKV stream as a bounded InputStream that repeats the same
     * clusters for a specified number of times.
     *
     * @param headerSize size of MKV header
     * @param clusterSize size of each cluster
     * @param numberOfClusters number of clusters
     * @return an InputStream instance
     * @throws IOException
     */
    public static InputStream asInputStream(int headerSize,
                                            int clusterSize,
                                            int numberOfClusters,
                                            Callback<ByteSource> callback) throws IOException {

        Iterable<ByteSource> byteSources = Iterables.concat(
                createHeaderSource(headerSize),
                createClusterIterable(clusterSize, numberOfClusters, callback));

        // Turn it into an infinite stream
        return ByteSource.concat(byteSources).openStream();
    }

    private static Iterable<ByteSource> createHeaderSource(final int headerSize) {
        return ImmutableList.of(randomBytes(headerSize));
    }

    private static Iterable<? extends ByteSource> createClusterIterable(final int clusterSize,
                                                                        final int numberOfClusters,
                                                                        final Callback<ByteSource> callback) {
        final Iterable<ByteSource> clustersContentSource = createClustersContentSource(clusterSize);

        final Iterable<ByteSource> iterable = numberOfClusters < 0
                ? Iterables.cycle(clustersContentSource)
                : Iterables.concat(Collections.nCopies(numberOfClusters, clustersContentSource));

        return new ObservableIterable<>(iterable, callback);
    }

    private static Iterable<ByteSource> createClustersContentSource(final int clusterSize) {
        final TimestampSource timestampSource = new TimestampSource();
        final ClusterBodySource clusterBodySource = new ClusterBodySource(clusterSize);

        // Every time this is onNext through, it will yield one header, one timestamp, one body
        return Iterables.concat(
                ImmutableList.of(CLUSTER_HEADER),
                timestampSource,
                clusterBodySource);
    }

    /**
     * Return byte source containing a specified number of random bytes.
     */

    private static Map<Integer, ByteSource> sBytesourceCache = new HashMap<>();

    private static ByteSource randomBytes(final Integer size) {
        ensureByteSourceInCache(size);
        return sBytesourceCache.get(size);
    }

    private static void ensureByteSourceInCache(final Integer size) {
        if (sBytesourceCache.containsKey(size)) {
            return;
        }

        byte[] body = new byte[size];
        RAND.nextBytes(body);
        sBytesourceCache.put(size, ByteSource.wrap(body));
    }

    public static interface Callback<T> {
        void onNext(final T element);
    }

    private static class ObservableIterable<T> implements Iterable<T> {

        private final Iterable<T> mIterable;
        private final Callback<T> mCallback;

        public ObservableIterable(final Iterable<T> iterable, final Callback callback) {
            mIterable = iterable;
            mCallback = callback;
        }

        @Override
        public Iterator<T> iterator() {
            return new ObservableIterator<>(mIterable.iterator(), mCallback);
        }
    }

    private static class ObservableIterator<T> implements Iterator<T> {

        private Iterator<T> mIterator;
        private Callback<T> mCallback;

        public ObservableIterator(final Iterator<T> iterator, final Callback<T> callback) {
            mIterator = iterator;
            mCallback = callback;
        }

        @Override
        public boolean hasNext() {
            return mIterator.hasNext();
        }

        @Override
        public T next() {
            final T next = mIterator.next();
            mCallback.onNext(next);
            return next;
        }
    }

    // An iterable that always yields a single timestamp encoding the current time
    private static class TimestampSource implements Iterable<ByteSource> {
        public TimestampSource() {  }

        @Override
        public Iterator<ByteSource> iterator() {
            return ImmutableList.of(mkvNumber(System.currentTimeMillis())).iterator();
        }

        private static ByteSource mkvNumber(long number) {
            return ByteSource.wrap(Longs.toByteArray(number));
        }
    }

    // An iterable that always yields a single source of random bytes
    private static class ClusterBodySource implements Iterable<ByteSource> {
        private final int size;

        public ClusterBodySource(int size) {
            this.size = size;
        }

        @SuppressWarnings("synthetic-access")
        @Override
        public Iterator<ByteSource> iterator() {
            return ImmutableList.of(randomBytes(size)).iterator();
        }
    }
}
