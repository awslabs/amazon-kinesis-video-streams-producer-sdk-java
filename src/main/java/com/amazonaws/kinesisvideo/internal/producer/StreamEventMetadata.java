package com.amazonaws.kinesisvideo.internal.producer;

import javax.annotation.Nullable;

/**
 * Kinesis Video stream event metadata as provided by the native codebase.
 */
public class StreamEventMetadata {

    // Current class version as described by the native struct.
    public static final int STREAM_EVENT_METADATA_CURRENT_VERSION = 0;


    // Version of the struct.
    int mVersion;

    // Optional s3 prefix.
    @Nullable
    String mImagePrefix;

    // Optional optimization, stating how many pairs to be appended.
    @Nullable
    byte mNumberOfPairs;

    // Optional custom data name/value pairs. String lengths must be <= MKV_MAX_TAG_NAME_LEN as defined in the native code.
    @Nullable
    String mNames[];
    //
    @Nullable
    String mValues[];


    public StreamEventMetadata(String imagePrefix, byte numberOfPairs, String names[], String values[]) {
        mVersion = STREAM_EVENT_METADATA_CURRENT_VERSION;
        mImagePrefix = imagePrefix;
        mNumberOfPairs = numberOfPairs;
        mNames = names;
        mValues = values;
    }

    public int getVersion() {
        return mVersion;
    }

    public String getImagePrefix() {
        return mImagePrefix;
    }

    public byte getNumberOfPairs() {
        return mNumberOfPairs;
    }
    public String[] getNames() {
        return mNames;
    }
    public String[] getValues() {
        return mValues;
    }

}
