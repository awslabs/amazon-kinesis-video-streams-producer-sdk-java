package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.auth.StaticCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni.PRODUCER_NATIVE_LIBRARY_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * Integration tests for multi-instance JNI producer functionality.
 *
 * <p>This test suite validates that the native Kinesis Video Producer JNI layer can handle
 * multiple concurrent instances without interference, memory leaks, or resource conflicts.
 *
 * <p>Scenarios tested:
 * <ul>
 *   <li>Sequential creation of multiple JNI instances</li>
 *   <li>Concurrent creation under high load ({@link #CONCURRENT_INSTANCE_COUNT} instances)</li>
 *   <li>Instance independence (destroying one doesn't affect others)</li>
 * </ul>
 */
public class MultiJniIntegTest {

    private static final Logger log = LogManager.getLogger(MultiJniIntegTest.class);

    private static final int DEFAULT_INSTANCE_COUNT = 3;
    private static final int CONCURRENT_INSTANCE_COUNT = 500;
    private static final int TEST_TIMEOUT_SECONDS = System.getenv("CI") != null ? 15 : 50;

    /**
     * Tracks all created JNI instances for proper cleanup
     * May contain nulls (to indicate the Jni instance was already freed)
     */
    private final List<NativeKinesisVideoProducerJni> jniInstances = new ArrayList<>();

    @Rule
    public Timeout globalTimeout = Timeout.seconds(TEST_TIMEOUT_SECONDS);

    /**
     * Initializes the test environment by loading the native JNI library.
     */
    @Before
    public void setUp() {
        try {
            System.loadLibrary(PRODUCER_NATIVE_LIBRARY_NAME);
            log.debug("Successfully loaded native library: {}", PRODUCER_NATIVE_LIBRARY_NAME);
        } catch (final UnsatisfiedLinkError e) {
            fail("JNI library not found. Ensure native library is built and available: " + e.getMessage());
        }
    }

    /**
     * Cleans up all created JNI instances.
     */
    @After
    public void tearDown() {
        boolean allInstancesFreedSuccessfully = true;

        for (int i = 0; i < this.jniInstances.size(); i++) {
            try {
                final NativeKinesisVideoProducerJni jni = this.jniInstances.get(i);
                if (jni != null) {
                    jni.free();
                    log.debug("Successfully freed JNI instance {}", i);
                }
            } catch (final Exception e) {
                log.error("Failed to free JNI instance {}: {}", i, e.getMessage());
                allInstancesFreedSuccessfully = false;
            }
        }

        this.jniInstances.clear();

        assertTrue("Failed to free some JNI instances - check logs for details",
                allInstancesFreedSuccessfully);
    }

    /**
     * Validates that multiple JNI instances can be created sequentially without interference.
     *
     * <p>This test ensures that the JNI layer properly manages multiple producer instances
     * within the same JVM, each with independent configurations and state.</p>
     */
    @Test
    public void whenCreatingMultipleJniInstances_thenAllInstancesInitializedWithoutErrors() throws Exception {
        // Given: Thread pools for async operations
        final ScheduledExecutorService authExecutorService =
                Executors.newScheduledThreadPool(DEFAULT_INSTANCE_COUNT);
        final ScheduledExecutorService serviceCallbacksExecutorService =
                Executors.newScheduledThreadPool(DEFAULT_INSTANCE_COUNT);

        try {
            // When: Creating multiple JNI instances with unique configurations
            for (int i = 0; i < DEFAULT_INSTANCE_COUNT; i++) {
                final NativeKinesisVideoProducerJni jni = createJniInstance(i, authExecutorService,
                        serviceCallbacksExecutorService);
                assertNotNull("JNI instance " + i + " should be created", jni);
                this.jniInstances.add(jni);
            }

            // Then: All instances should be created successfully
            assertEquals("All JNI instances should be created",
                    DEFAULT_INSTANCE_COUNT, this.jniInstances.size());

            // And: Each instance should be properly initialized and ready
            initializeAllInstances();
            verifyAllInstancesReady();

        } finally {
            shutdownExecutorServices(authExecutorService, serviceCallbacksExecutorService);
        }
    }

    /**
     * Validates that JNI instances can be created concurrently under high load.
     *
     * <p>This stress test creates {@value #CONCURRENT_INSTANCE_COUNT} instances simultaneously to verify
     * thread safety and proper resource management in the native JNI layer. This simulates real-world
     * scenarios where multiple clients are initialized concurrently.</p>
     */
    @Test
    public void givenHighConcurrencyEnvironment_whenCreatingMultipleJniInstancesConcurrently_thenAllInstancesAreInitializedWithoutErrors() throws Exception {
        // Given: High-concurrency execution environment
        final String testContext = buildTestContext();
        final ScheduledExecutorService authExecutorService =
                Executors.newScheduledThreadPool(CONCURRENT_INSTANCE_COUNT);
        final ScheduledExecutorService serviceCallbacksExecutorService =
                Executors.newScheduledThreadPool(CONCURRENT_INSTANCE_COUNT);
        final ExecutorService creationExecutor = Executors.newFixedThreadPool(CONCURRENT_INSTANCE_COUNT);

        try {
            // When: Creating instances concurrently with synchronized start
            final List<Future<NativeKinesisVideoProducerJni>> creationFutures =
                    submitConcurrentCreationTasks(testContext, authExecutorService,
                            serviceCallbacksExecutorService, creationExecutor);

            // Then: All instances should be created successfully
            collectAndValidateConcurrentResults(creationFutures);

            log.info("Successfully created {} concurrent JNI instances", CONCURRENT_INSTANCE_COUNT);

        } finally {
            shutdownExecutorServices(authExecutorService, serviceCallbacksExecutorService, creationExecutor);
        }
    }


    /**
     * Validates that JNI instances can be created and destroyed in a staggered pattern.
     *
     * <p>This test creates JNI instances every 50ms, waits a random time between 50-150ms,
     * then destroys them. This pattern continues for 5 seconds to test resource management
     * under continuous create/destroy cycles.</p>
     */
    @Test
    public void givenStaggeredCreateAndDestroyPattern_whenRunningForFiveSeconds_thenAllOperationsCompleteSuccessfully() throws Exception {
        final int gracePeriodSeconds = 7;
        assumeTrue(TEST_TIMEOUT_SECONDS > gracePeriodSeconds);

        final ScheduledExecutorService staggeredExecutor = Executors.newScheduledThreadPool(75,
                new ThreadFactoryBuilder().setNameFormat("staggered-test-%d").build());
        final ScheduledExecutorService authExecutor = Executors.newScheduledThreadPool(75,
                new ThreadFactoryBuilder().setNameFormat("staggered-auth-%d").build());
        final ScheduledExecutorService serviceExecutor = Executors.newScheduledThreadPool(75,
                new ThreadFactoryBuilder().setNameFormat("staggered-service-%d").build());
        
        try {
            final long testDurationMs = (TEST_TIMEOUT_SECONDS - gracePeriodSeconds) * 1000L;
            final long startTime = System.currentTimeMillis();
            
            while (System.currentTimeMillis() - startTime < testDurationMs) {
                staggeredExecutor.submit(() -> {
                    try {
                        final NativeKinesisVideoProducerJni jni = createAndInitializeJniInstance(
                            "staggered-test", (int) (System.currentTimeMillis() % 1000),
                            authExecutor, serviceExecutor);
                        
                        final int waitTime = (int) (Math.random() * 200);
                        Thread.sleep(waitTime);
                        
                        jni.free();
                    } catch (Exception e) {
                        log.error("Error in staggered create/destroy task", e);
                        fail("Staggered task failed: " + e.getMessage());
                    }
                });
                
                Thread.sleep((long) (Math.random() * 25));
            }
            
            log.info("Staggered test completed successfully");
            
        } finally {
            shutdownExecutorServices(staggeredExecutor, authExecutor, serviceExecutor);
        }
    }

    /**
     * Validates that JNI instances operate independently without affecting each other.
     *
     * <p>This test ensures that destroying one instance doesn't corrupt or interfere
     * with other active instances, which is critical for robust multi-tenant operation.</p>
     */
    @Test
    public void givenMultipleActiveJniInstances_whenDestroyingOneInstance_thenOtherInstancesRemainFunctional() throws Exception {
        // Given: Multiple active JNI instances
        whenCreatingMultipleJniInstances_thenAllInstancesInitializedWithoutErrors();
        verifyAllInstancesReady();

        // When: Destroying the middle instance
        final int instanceToDestroy = 1;
        log.info("Destroying JNI instance {} to test independence", instanceToDestroy);
        this.jniInstances.get(instanceToDestroy).free();
        this.jniInstances.set(instanceToDestroy, null);

        // Then: Other instances should remain functional
        assertTrue("Instance 0 should remain ready after destroying instance 1",
                this.jniInstances.get(0).isReady());
        assertTrue("Instance 2 should remain ready after destroying instance 1",
                this.jniInstances.get(2).isReady());

        // And: New instances can still be created (JNI layer is not corrupted)
        final NativeKinesisVideoProducerJni newInstance = createSingleJniInstance(generateUniqueDeviceName("post-destruction-test"));
        try {
            assertTrue("New instance should be ready after previous destruction",
                    newInstance.isReady());
        } finally {
            newInstance.free();
        }
    }

    // ========== Helper Methods ==========

    /**
     * Initializes all created JNI instances with unique device configurations.
     */
    private void initializeAllInstances() throws Exception {
        for (final NativeKinesisVideoProducerJni jniInstance : this.jniInstances) {
            final String deviceName = generateUniqueDeviceName("sequential-test");
            final DeviceInfo deviceInfo = createTestDeviceInfo(deviceName);
            jniInstance.createSync(deviceInfo);
        }
    }

    /**
     * Verifies that all JNI instances are ready for operation.
     */
    private void verifyAllInstancesReady() {
        for (int i = 0; i < this.jniInstances.size(); i++) {
            assertTrue("JNI instance " + i + " should be ready",
                    this.jniInstances.get(i).isReady());
        }
    }

    /**
     * Builds test context string for concurrent tests.
     */
    private String buildTestContext() {
        return String.join("-", getClass().getSimpleName(), "testConcurrentJniCreation");
    }

    /**
     * Submits concurrent JNI creation tasks with synchronized start.
     */
    private List<Future<NativeKinesisVideoProducerJni>> submitConcurrentCreationTasks(
            final String testContext,
            final ScheduledExecutorService authExecutor,
            final ScheduledExecutorService serviceExecutor,
            final ExecutorService creationExecutor) {

        final List<Future<NativeKinesisVideoProducerJni>> futures = new ArrayList<>();
        final CountDownLatch startLatch = new CountDownLatch(1);

        for (int i = 0; i < CONCURRENT_INSTANCE_COUNT; i++) {
            final int index = i;
            final Future<NativeKinesisVideoProducerJni> future = creationExecutor.submit(() -> {
                try {
                    startLatch.await(); // Synchronized start
                    return createAndInitializeJniInstance(testContext, index, authExecutor, serviceExecutor);
                } catch (final Exception e) {
                    log.error("Failed to create concurrent JNI instance {}", index, e);
                    fail("Failed to create concurrent JNI instance " + index + ": " + e.getMessage());
                    return null;
                }
            });
            futures.add(future);
        }

        // Start all tasks simultaneously
        startLatch.countDown();
        return futures;
    }

    /**
     * Collects and validates results from concurrent creation tasks.
     */
    private void collectAndValidateConcurrentResults(
            final List<Future<NativeKinesisVideoProducerJni>> futures) throws Exception {

        for (int i = 0; i < CONCURRENT_INSTANCE_COUNT; i++) {
            final NativeKinesisVideoProducerJni jni = futures.get(i).get(10, TimeUnit.SECONDS);
            assertNotNull("Concurrent JNI instance " + i + " should be created", jni);
            assertTrue("Concurrent JNI instance " + i + " should be ready", jni.isReady());
            this.jniInstances.add(jni);
        }

        assertEquals("All concurrent instances should be created",
                CONCURRENT_INSTANCE_COUNT, this.jniInstances.size());
    }

    /**
     * Creates and initializes a single JNI instance for testing.
     */
    private NativeKinesisVideoProducerJni createSingleJniInstance(final String testContext) throws Exception {
        final ScheduledExecutorService authExecutor = Executors.newScheduledThreadPool(1);
        final ScheduledExecutorService serviceExecutor = Executors.newScheduledThreadPool(1);

        try {
            return createAndInitializeJniInstance(
                    testContext, 0, authExecutor, serviceExecutor);
        } finally {
            shutdownExecutorServices(authExecutor, serviceExecutor);
        }
    }

    /**
     * Creates a JNI instance with the specified configuration.
     */
    private NativeKinesisVideoProducerJni createJniInstance(final int index,
                                                            final ScheduledExecutorService authExecutor,
                                                            final ScheduledExecutorService serviceExecutor) throws ProducerException {
        final KinesisVideoCredentialsProvider credentialsProvider =
                new StaticCredentialsProvider(new KinesisVideoCredentials("ak" + index, "sk" + index));

        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(credentialsProvider)
                .build();

        return new NativeKinesisVideoProducerJni(
                new DefaultAuthCallbacks(credentialsProvider, authExecutor,
                        LogManager.getLogger(NativeKinesisVideoProducerJni.class)),
                new DefaultStorageCallbacks(),
                new DefaultServiceCallbacksImpl(
                        LogManager.getLogger(DefaultServiceCallbacksImpl.class),
                        serviceExecutor, configuration, new JavaKinesisVideoServiceClient())
        );
    }

    /**
     * Creates and initializes a JNI instance with the specified configuration.
     *
     * @param index           used to identify the JNI instance
     * @param testContext     used to identify the JNI instance
     * @param authExecutor    used for the auth callbacks
     * @param serviceExecutor used for the service callbacks
     */
    private NativeKinesisVideoProducerJni createAndInitializeJniInstance(
            final String testContext, final int index,
            final ScheduledExecutorService authExecutor,
            final ScheduledExecutorService serviceExecutor) throws Exception {
        assumeNotNull("testContext cannot be null", testContext);
        assumeTrue("Index should not be negative", index >= 0);
        assumeNotNull("authExecutor cannot be null", authExecutor);
        assumeNotNull("serviceExecutor cannot be null", serviceExecutor);

        final String accessKey = String.join("-", testContext, "ak", String.valueOf(index));
        final String secretKey = String.join("-", testContext, "sk", String.valueOf(index));
        final String deviceName = String.join("-", testContext, "device", String.valueOf(index));

        final KinesisVideoCredentialsProvider credentialsProvider =
                new StaticCredentialsProvider(new KinesisVideoCredentials(accessKey, secretKey));

        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(credentialsProvider)
                .build();

        final NativeKinesisVideoProducerJni jni = new NativeKinesisVideoProducerJni(
                new DefaultAuthCallbacks(credentialsProvider, authExecutor,
                        LogManager.getLogger(NativeKinesisVideoProducerJni.class)),
                new DefaultStorageCallbacks(),
                new DefaultServiceCallbacksImpl(
                        LogManager.getLogger(DefaultServiceCallbacksImpl.class),
                        serviceExecutor, configuration, new JavaKinesisVideoServiceClient())
        );

        final DeviceInfo deviceInfo = createTestDeviceInfo(deviceName);
        jni.createSync(deviceInfo);

        return jni;
    }

    /**
     * Generates a unique device name for testing using the prefix.
     */
    @Nonnull
    private String generateUniqueDeviceName(@Nonnull final String prefix) {
        assumeNotNull("Prefix cannot be null!", prefix);

        return String.join("-", prefix, String.valueOf(System.currentTimeMillis()),
                UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Safely shuts down executor services with timeout.
     */
    private void shutdownExecutorServices(@Nonnull final ExecutorService... executors) {
        assumeNotNull("Executors cannot be null!", executors);

        for (final ExecutorService executor : executors) {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("Executor did not terminate within the timeout");
                        fail("Executor did not terminate within the timeout");
                    }
                } catch (final Exception e) {
                    log.error("Executor did not terminate gracefully", e);
                    fail("Executor terminated with exception: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Creates test device information with standardized configuration.
     *
     * @param deviceName unique device identifier
     * @return configured DeviceInfo for testing
     */
    @Nonnull
    @SuppressWarnings("ConstantConditions")
    private DeviceInfo createTestDeviceInfo(@Nonnull final String deviceName) {
        assumeNotNull("Device name cannot be null", deviceName);

        final int storageInfoVersion = 0;
        final StorageInfo.DeviceStorageType storageType = StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM;
        final long storageSizeBytes = 1024 * 1024 * 10; // 10 MB
        final int spillRatio = 90;
        final String rootDirectory = "/tmp";
        final StorageInfo storageInfo = new StorageInfo(storageInfoVersion,
                storageType,
                storageSizeBytes,
                spillRatio,
                rootDirectory);

        final int deviceInfoVersion = 0;
        final Tag[] tags = null;
        final int numStreams = 1;
        return new DeviceInfo(deviceInfoVersion,
                deviceName,
                storageInfo,
                numStreams,
                tags);
    }
}
