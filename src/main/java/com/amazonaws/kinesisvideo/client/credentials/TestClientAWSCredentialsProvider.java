package com.amazonaws.kinesisvideo.client.credentials;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collection;
import java.util.List;

public class TestClientAWSCredentialsProvider implements AWSCredentialsProvider {

    private List<Provider<AWSCredentialsProvider>> mCredentialsProviders;

    @Inject
    public TestClientAWSCredentialsProvider(final Collection<Provider<AWSCredentialsProvider>> providers) {
        mCredentialsProviders = ImmutableList.copyOf(providers);
    }

    @Override
    public AWSCredentials getCredentials() {
        for (final Provider<AWSCredentialsProvider> provider : mCredentialsProviders) {
            final AWSCredentials awsCredentials = tryGetCredentialsFrom(provider);
            if (awsCredentials != null) {
                return awsCredentials;
            }
        }

        throw new RuntimeException("Unable to load AWS credentials");
    }

    private static AWSCredentials tryGetCredentialsFrom(final Provider<AWSCredentialsProvider> provider) {
        try {
            return provider.get().getCredentials();
        } catch (final Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void refresh() {
        for (final Provider<AWSCredentialsProvider> provider : mCredentialsProviders) {
            provider.get().refresh();
        }
    }
}

