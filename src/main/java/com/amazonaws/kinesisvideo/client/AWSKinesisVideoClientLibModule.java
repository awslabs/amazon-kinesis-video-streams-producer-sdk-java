package com.amazonaws.kinesisvideo.client;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.kinesisvideo.client.credentials.TestClientAWSCredentialsProvider;
import com.amazonaws.kinesisvideo.client.signing.AWSKinesisVideoV4Signer;
import com.amazonaws.kinesisvideo.client.signing.KinesisVideoAWS4Signer;
import com.amazonaws.kinesisvideo.config.ClientConfiguration;
import com.amazonaws.kinesisvideo.signing.KinesisVideoSigner;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;

public class AWSKinesisVideoClientLibModule extends AbstractModule {

    private final ClientConfiguration mClientConfiguration;

    public AWSKinesisVideoClientLibModule(final ClientConfiguration clientConfiguration) {
        mClientConfiguration = clientConfiguration;
    }

    @Override
    protected void configure() {
        bindAWSCredentialsProviders();
        bind(AWSCredentialsProvider.class).to(TestClientAWSCredentialsProvider.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    public ClientConfiguration getConfig() {
        return mClientConfiguration;
    }

    private void bindAWSCredentialsProviders() {
        final Multibinder<AWSCredentialsProvider> credentialsProviders =
                Multibinder.newSetBinder(binder(), AWSCredentialsProvider.class);

        credentialsProviders.addBinding().to(EnvironmentVariableCredentialsProvider.class).in(Singleton.class);
        credentialsProviders.addBinding().to(ProfileCredentialsProvider.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    public KinesisVideoSigner getSigner(final TestClientAWSCredentialsProvider credentialsProvider) {
        return new KinesisVideoAWS4Signer(credentialsProvider, mClientConfiguration);
    }

    @Provides
    @Singleton
    @Named("AWSKinesisVideoV4SignerForStreamingPayload")
    public KinesisVideoSigner getAWSKinesisVideoV4SignerForStreamingPayload(
                           final TestClientAWSCredentialsProvider credentialsProvider) {
        return new AWSKinesisVideoV4Signer(credentialsProvider, mClientConfiguration, true);
    }

    @Provides
    @Singleton
    @Named("AWSKinesisVideoV4SignerForNonStreamingPayload")
    public KinesisVideoSigner getAWSKinesisVideoV4SignerForNonStreamingPayload(
                           final TestClientAWSCredentialsProvider credentialsProvider) {
        return new AWSKinesisVideoV4Signer(credentialsProvider, mClientConfiguration, false);
    }
}
