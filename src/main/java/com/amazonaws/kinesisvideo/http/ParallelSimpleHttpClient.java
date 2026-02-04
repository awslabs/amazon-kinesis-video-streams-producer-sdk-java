package com.amazonaws.kinesisvideo.http;

import com.amazonaws.kinesisvideo.client.IPVersionFilter;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfigurationDefaults;
import com.amazonaws.kinesisvideo.common.function.Consumer;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.socket.SocketFactory;
import com.amazonaws.kinesisvideo.util.LoggedExitRunnable;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.amazonaws.kinesisvideo.common.preconditions.Preconditions.checkNotNull;

public final class ParallelSimpleHttpClient implements HttpClient {

    private static final int AWAIT_THREAD_TERMINATE_SECS = 3;

    private static final String SPACE = " ";
    private static final String CLRF = "\r\n";
    private static final String HTTP_1_1 = "HTTP/1.1";
    private static final String HEADER_FORMAT = "%s: %s";
    private static final String HOST_HEADER = "Host";
    private static final Consumer<OutputStream> NO_OP_SENDER = new Consumer<OutputStream>() {
        @Override
        public void accept(final OutputStream outputStream) {
            // no op;
        }
    };
    private static final Consumer<Exception> NO_OP_COMPLETION = new Consumer<Exception>() {
        @Override
        public void accept(final Exception object) {
            // No op;
        }
    };
    private final Logger log;
    private final Builder mBuilder;
    private Socket mSocket;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private ExecutorService payloadSender;
    private ExecutorService responseReceiver;
    private final List<ExitResult> exitHistory = new ArrayList<>();

    private enum Caller {
        SENDER,
        RECEIVER,
        CLOSE
    }

    private static class ExitResult {
        @Nonnull
        private Caller caller;

        @Nullable
        private Exception exception;

        ExitResult(@Nonnull final Caller caller, @Nullable final Exception exception) {
            this.caller = caller;
            this.exception = exception;
        }

        @Override
        public String toString() {
            return "ExitResult{" +
                    "caller=" + caller +
                    ", exception=" + exception +
                    '}';
        }
    }

    private ParallelSimpleHttpClient(final Builder builder) {
        mBuilder = builder;
        log = LogManager.getLogger(ParallelSimpleHttpClient.class);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void connectAndProcessInBackground() {
        try {
            checkNotNull(mBuilder.mReceiver, "No callback set for the receiver!");
            initSocket();
            startCommunication();
        } catch (final Throwable e) {
            throw new RuntimeException("Exception while connecting to the server ! ", e);
        }
    }

    private void initSocket() throws IOException {
        mSocket = new SocketFactory().createSocket(mBuilder.mUri, mBuilder.mIPVersionFilter);
        if (mBuilder.mTimeout != null) {
            mSocket.setSoTimeout(mBuilder.mTimeout);
        }
        mInputStream = mSocket.getInputStream();
        mOutputStream = mSocket.getOutputStream();
    }

    public InputStream connectAndGetResponse() {
        try {
            initSocket();
            sendInitRequest();
        } catch (final Exception e) {
            throw new RuntimeException("Exception while executing and returning response ! ", e);
        }
        return mInputStream;
    }

    private void startCommunication() throws Exception {
        sendInitRequest();
        sendPayloadInBackground();
        receiveResponseInBackground();
    }

    private void sendInitRequest() throws Exception {
        final Writer outputWriter = new BufferedWriter(new OutputStreamWriter(mOutputStream, Charset.defaultCharset()));
        final String initRequest = new StringBuilder().append(getHttpRequestString()).append(getHeadersString()).append(CLRF).toString();
        log.debug("Request: {}", initRequest);
        outputWriter.write(initRequest);
        outputWriter.flush();
    }

    private String getHttpRequestString() {
        final StringBuilder httpRequest = new StringBuilder();
        return httpRequest.append(mBuilder.mMethod).append(SPACE).append(mBuilder.mUri.getPath()).append(SPACE).append(HTTP_1_1).append(CLRF).toString();
    }

    @Override
    public HttpMethodName getMethod() {
        return mBuilder.mMethod;
    }

    @Override
    public URI getUri() {
        return mBuilder.mUri;
    }

    @Override
    public Map<String, String> getHeaders() {
        return mBuilder.mHeaders;
    }

    @Override
    public InputStream getContent() {
        return null;
    }

    private String getHeadersString() {
        final StringBuilder builder = new StringBuilder();
        for (final Map.Entry<String, String> header : mBuilder.mHeaders.entrySet()) {
            final String headerString = String.format(HEADER_FORMAT, header.getKey(), header.getValue());
            builder.append(headerString);
            builder.append(CLRF);
        }
        final String allHeaders = builder.toString();
        return allHeaders.isEmpty() ? CLRF : allHeaders;
    }

    private void sendPayloadInBackground() {
        if (mBuilder.mSender != null) {
            final String postfix = computeThreadNamePostfix();
            payloadSender = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("PutMedia-Sending" + postfix).build());
            payloadSender.execute(new LoggedExitRunnable("PutMedia-Sending-" + postfix) {
                @Override
                public void execute() {
                    Exception storedException = null;
                    try {
                        // This is needed to get the thread Id.
                        log.debug("[{}] Start sending data.", mBuilder.mStreamName);
                        mBuilder.mSender.accept(mOutputStream);
                        log.debug("[{}] End sending data. Sent all data, close.", mBuilder.mStreamName);
                    } catch (final Exception e) {
                        log.error("[{}] Exception thrown on sending thread", mBuilder.mStreamName, e);
                        storedException = e;
                    } finally {
                        notifyCompletionCallback(new ExitResult(Caller.SENDER, storedException));
                        payloadSender.shutdownNow();
                    }
                }
            });
        }
    }

    private void receiveResponseInBackground() {
        if (mBuilder.mReceiver != null) {
            final String postfix = computeThreadNamePostfix();
            responseReceiver = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("PutMedia-Receiving" + postfix).build());
            responseReceiver.execute(new LoggedExitRunnable("PutMedia-Receiving" + postfix) {
                @Override
                public void execute() {
                    Exception storedException = null;
                    try {
                        log.debug("[{}] Starting receiving data", mBuilder.mStreamName);
                        mBuilder.mReceiver.accept(mInputStream);
                        log.debug("[{}] Received all data, close", mBuilder.mStreamName);
                    } catch (final Exception e) {
                        log.error("[{}] Exception thrown on receiving thread", mBuilder.mStreamName, e);
                        storedException = e;
                    } finally {
                        notifyCompletionCallback(new ExitResult(Caller.RECEIVER, storedException));
                        responseReceiver.shutdownNow();
                        closeSocket();
                    }
                }
            });
        }
    }

    /**
     * Intended to be immediately concatenated to the end of the PutMedia-Sender and PutMedia-Receiver thread names.
     * The purpose is to make it easier to identify and troubleshoot a particular PutMedia connection.
     * This always includes the dash at the beginning.
     *
     * @return {
     *  <ol>
     *      <li>"-{streamName}-{sessionId}" if both are present</li>
     *      <li>"-{streamName}" if only streamName is present</li>
     *      <li>"-{sessionId}" if only sessionId is present</li>
     *      <li>"-{UUID}" if neither are present</li>
     *  </ol>
     *  }
     */
    private String computeThreadNamePostfix() {
        final StringBuilder builder = new StringBuilder();
        if (mBuilder.mStreamName != null && !mBuilder.mStreamName.isEmpty()) {
            builder.append("-");
            builder.append(mBuilder.mStreamName);
        }

        if (mBuilder.mSessionId != null && !mBuilder.mSessionId.isEmpty()) {
            builder.append("-");
            builder.append(mBuilder.mSessionId);
        }

        if (builder.length() == 0) {
            builder.append("-");
            builder.append(UUID.randomUUID());
        }

        return builder.toString();
    }

    public void closeSocket() {
        try {
            mSocket.close();
            //Ideally socket close should close this but also explicitly closing the streams
            //as it will fail silently if already closed.
            mInputStream.close();
            mOutputStream.close();
        } catch (final Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("Exception while shutting down!", e);
        }
    }

    @Override
    public void close() throws IOException {
        payloadSender.shutdownNow();
        responseReceiver.shutdownNow();
        closeSocket();

        awaitTryShutdownThreads();

        notifyCompletionCallback(new ExitResult(Caller.CLOSE, null));
    }

    // This is used to synchronize the 3 threads which call the completion callback:
    // - Sender thread
    // - Receiving ACKs thread
    // - Thread calling close()
    // If close() is called, it will immediately invoke the completion callback with success.
    // Otherwise, it will wait for both sender and receiver threads to exit before notifying.
    // If applicable, the thread that threw the exception first's result will be propagated.
    private void notifyCompletionCallback(@Nonnull final ExitResult exitResult) {
        // Note: the thread name should already have the stream name + connection handle # in it
        log.debug("Received: {}", exitResult);

        if (mBuilder.mCompletion != null) {

            if (exitResult.caller == Caller.CLOSE) {
                mBuilder.mCompletion.accept(null);
                return;
            }

            Exception exceptionToNotify = null;
            boolean notify = false;
            synchronized (this.exitHistory) {
                this.exitHistory.add(exitResult);

                if (this.exitHistory.size() == 2 &&
                        ((this.exitHistory.get(0).caller == Caller.SENDER && this.exitHistory.get(1).caller == Caller.RECEIVER) ||
                        (this.exitHistory.get(0).caller == Caller.RECEIVER && this.exitHistory.get(1).caller == Caller.SENDER))
                ) {
                    // Check if either one of them exited with an exception
                    // If so, propagate it. If both of them terminated normally, notify with null
                    notify = true;

                    // prioritize the exception that came first
                    exceptionToNotify = this.exitHistory.get(0).exception;
                    if (exceptionToNotify == null) {
                        exceptionToNotify = this.exitHistory.get(1).exception;
                    }
                } else {
                    log.debug("Not notifying this time, caller history: {}", this.exitHistory);
                }
            }

            if (notify) {
                log.debug("[{}] notifying completion callback with {}", mBuilder.mStreamName, exceptionToNotify);
                mBuilder.mCompletion.accept(exceptionToNotify);
            }
        }
    }

    // Wait for the threads to terminate
    // If the threads are not alive, returns immediately
    // Expecting these to be near instantaneous
    private void awaitTryShutdownThreads() {
        awaitTermination(this.payloadSender, "payload sender", AWAIT_THREAD_TERMINATE_SECS);
        awaitTermination(this.responseReceiver, "response receiver", AWAIT_THREAD_TERMINATE_SECS);
    }

    @SuppressWarnings("ConstantConditions")
    private void awaitTermination(@Nonnull final ExecutorService executor, @Nonnull final String id,
                                  final int threadTerminateTimeoutSeconds) {
        Preconditions.checkArgument(executor != null, "Executor cannot be null");
        Preconditions.checkArgument(id != null, "ID cannot be null");
        Preconditions.checkArgument(threadTerminateTimeoutSeconds >= 0, "ThreadTerminateTimeoutSeconds must be positive");

        try {
            if (!executor.awaitTermination(AWAIT_THREAD_TERMINATE_SECS, TimeUnit.SECONDS)) {
                log.error("{}: {} couldn't shutdown within {} seconds", mBuilder.mStreamName, id, AWAIT_THREAD_TERMINATE_SECS);
            }
        } catch (final InterruptedException e) {
            log.error("{}: Interrupted while waiting for {} shutdown", mBuilder.mStreamName, id, e);
            Thread.currentThread().interrupt();
        }
    }


    public static final class Builder {
        private final Map<String, String> mHeaders;
        private URI mUri;
        private HttpMethodName mMethod;
        private Consumer<OutputStream> mSender;
        private Consumer<InputStream> mReceiver;
        private Integer mTimeout;
        private IPVersionFilter mIPVersionFilter;
        private Consumer<Exception> mCompletion;
        private String mStreamName = "";
        private String mSessionId = "";
        // TODO: Set to correct output channel

        private Builder() {
            mHeaders = new HashMap<>();
            mSender = NO_OP_SENDER;
            mCompletion = NO_OP_COMPLETION;
            mIPVersionFilter = KinesisVideoClientConfigurationDefaults.BOTH_IPV4_AND_IPV6;
        }

        public Builder uri(final URI uri) {
            mUri = uri;
            mHeaders.put(HOST_HEADER, uri.getHost());
            return this;
        }

        public Builder method(final HttpMethodName method) {
            mMethod = method;
            return this;
        }

        public Builder header(final String key, final String value) {
            mHeaders.put(key, value);
            return this;
        }

        public Builder completionCallback(final Consumer<Exception> completion) {
            // Make sure we don't override the default no-op
            if (completion != null) {
                mCompletion = completion;
            }
            return this;
        }

        public Builder setSenderCallback(final Consumer<OutputStream> sender) {
            mSender = sender;
            return this;
        }

        public Builder setReceiverCallback(final Consumer<InputStream> receiver) {
            mReceiver = receiver;
            return this;
        }

        public Builder setTimeout(final Integer timeout) {
            mTimeout = timeout;
            return this;
        }

        public Builder setIPVersionFilter(final IPVersionFilter ipVersionFilter) {
            mIPVersionFilter = ipVersionFilter;
            return this;
        }

        public Builder setStreamName(final String streamName) {
            mStreamName = streamName;
            return this;
        }

        public Builder setSessionId(final String sessionId) {
            mSessionId = sessionId;
            return this;
        }

        public ParallelSimpleHttpClient build() {
            checkNotNull(mUri);
            return new ParallelSimpleHttpClient(this);
        }
    }
}
