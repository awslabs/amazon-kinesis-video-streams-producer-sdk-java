package com.amazonaws.kinesisvideo.client.mediasource;

/**
 * Thrown when trying to create the media source of type unknown to the MediaSourceService.
 */
public class UnknownMediaSourceException extends RuntimeException {

    public UnknownMediaSourceException(final String mediaSourceType) {
        super("Unknown media source type '" + mediaSourceType
                + "'. Cannot create instance from the configuration");
    }
}
