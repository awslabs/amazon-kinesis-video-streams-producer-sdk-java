package com.amazonaws.kinesisvideo.internal.producer.client;

import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.function.Consumer;
import com.amazonaws.kinesisvideo.producer.StreamDescription;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.Date;
import java.util.Map;

public interface KinesisVideoServiceClient {

    /**
     * Initialize with the network configuration.
     *
     * @param configuration - Client configuration to initialize with
     */
    void initialize(@Nonnull final KinesisVideoClientConfiguration configuration)
            throws KinesisVideoException;

    /**
     * Create a stream on KinesisVideo frontend
     *
     * @param streamName             - Name of stream to create
     * @param deviceName             - Device name of stream to create
     * @param contentType            - Content type of stream to create
     * @param kmsKeyId               - KMS Key Id
     * @param retentionPeriodInHours - Stream retention period in hours
     * @param timeoutInMillis        - Timeout in milliseconds
     * @param kinesisVideoCredentialsProvider - Credentials to use
     * @return StreamArn of stream just created
     */
    String createStream(@Nonnull final String streamName,
                        @Nonnull final String deviceName,
                        @Nonnull final String contentType,
                        @Nullable final String kmsKeyId,
                        long retentionPeriodInHours,
                        long timeoutInMillis,
                        @Nullable final KinesisVideoCredentialsProvider kinesisVideoCredentialsProvider)
            throws KinesisVideoException;

    /**
     * Describe the status of a stream
     *
     * @param streamName             - Name of stream to describe
     * @param timeoutInMillis        - Timeout in milliseconds
     * @param kinesisVideoCredentialsProvider - Credentials to use
     * @return Stream description
     */
    StreamDescription describeStream(@Nonnull final String streamName,
                                     long timeoutInMillis,
                                     @Nullable final KinesisVideoCredentialsProvider kinesisVideoCredentialsProvider)
            throws KinesisVideoException;

    /**
     * Delete stream with name and version
     *
     * @param streamName             - Name of stream to delete
     * @param version                - Version of stream to delete
     * @param creationTime           - Creation time of stream to delete
     * @param timeoutInMillis        - Timeout in milliseconds
     * @param kinesisVideoCredentialsProvider - Credentials to use
     */
    void deleteStream(@Nonnull final String streamName,
                      @Nonnull final String version,
                      final Date creationTime,
                      long timeoutInMillis,
                      @Nullable final KinesisVideoCredentialsProvider kinesisVideoCredentialsProvider)
            throws KinesisVideoException;

    /**
     * Tag a stream of a specified ARN with a list of tags.
     *
     * @param streamArn              - ARN of the stream to be tagged
     * @param tags                   - Map of key-value pair
     * @param timeoutInMillis        - Timeout in milliseconds
     * @param kinesisVideoCredentialsProvider - Credentials to use
     */
    void tagStream(@Nonnull final String streamArn,
                   @Nullable final Map<String, String> tags,
                   long timeoutInMillis,
                   @Nullable final KinesisVideoCredentialsProvider kinesisVideoCredentialsProvider)
            throws KinesisVideoException;

    /**
     * Get data endpoint to be used by PutMedia for the specified stream.
     *
     * @param streamName             - Name of the stream
     * @param apiName                - Api name to call
     * @param timeoutInMillis        - Timeout in milliseconds
     * @param kinesisVideoCredentialsProvider - Credentials to use
     * @return endpoint to which PutMedia API is sent
     */
    String getDataEndpoint(@Nonnull final String streamName,
                           @Nonnull final String apiName,
                           long timeoutInMillis,
                           @Nullable final KinesisVideoCredentialsProvider kinesisVideoCredentialsProvider)
            throws KinesisVideoException;

    /**
     * Puts media as a long-running operation.
     * <p>
     * NOTE: The call will be 'prompt' and will return the IO streams which will be used for the actual streaming.
     *
     * @param streamName                - Name of the stream
     * @param containerType             - Container type
     * @param streamStartTimeInMillis   - Stream start time
     * @param absoluteFragmentTimes     - Whether to use absolute fragment times
     * @param ackRequired               - Whether acks are required
     * @param dataEndpoint              - The data endpoint to use
     * @param timeoutInMillis           - Timeout in milliseconds
     * @param kinesisVideoCredentialsProvider - Credentials to use
     * @param dataStream                - Data {@link InputStream}
     * @param acksConsumer              - Consumer of the ACK input stream
     * @param completionCallback        - Consumer of an Exception for reporting stream termination
     */
    void putMedia(@Nonnull final String streamName,
                  @Nonnull final String containerType,
                  final long streamStartTimeInMillis,
                  final boolean absoluteFragmentTimes,
                  final boolean ackRequired,
                  @Nonnull final String dataEndpoint,
                  long timeoutInMillis,
                  @Nullable final KinesisVideoCredentialsProvider kinesisVideoCredentialsProvider,
                  @Nonnull final InputStream dataStream,
                  @Nonnull final Consumer<InputStream> acksConsumer,
                  @Nullable final Consumer<Exception> completionCallback)
            throws KinesisVideoException;
}
