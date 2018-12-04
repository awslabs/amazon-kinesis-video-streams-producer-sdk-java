package com.amazonaws.kinesisvideo.client.mediasource;

import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;

/**
 * Thrown when MediaSourceService determines that the media source configuration is not supported
 */
public class UnsupportedConfigurationException extends RuntimeException {
    UnsupportedConfigurationException(final MediaSourceConfiguration mediaSourceConfiguration) {
        super("Configuration is not supported: '" + mediaSourceConfiguration.toString() + "'");
    }
}
