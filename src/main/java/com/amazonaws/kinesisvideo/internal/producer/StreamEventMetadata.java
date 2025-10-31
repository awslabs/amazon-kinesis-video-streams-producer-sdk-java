package com.amazonaws.kinesisvideo.internal.producer;

import javax.annotation.Nullable;

/**
 * Kinesis Video stream event metadata as provided by the native codebase.
 */
public class StreamEventMetadata {

    // Current class version as described by the native struct.
    public static final int STREAM_EVENT_METADATA_CURRENT_VERSION = 0;


    // Version of the struct.
    private final int mVersion;

    // Optional s3 prefix.
    @Nullable
    private final String mImagePrefix;

    // Optional optimization stating how many name/value pairs to be appended.
    // NOTE: This is NOT optional if using mNames and mValues, else the arrays will not be used.
    private final int mNumberOfPairs;

    // Optional custom data name/value pairs.
    // String lengths must be <= MKV_MAX_TAG_NAME_LEN as defined in the native code.
    @Nullable
    private final String[] mNames;
    //
    // String lengths must be <= MKV_MAX_TAG_VALUE_LEN as defined in the native code.
    @Nullable
    private final String[] mValues;

    /**
    * Create a new StreamEventMetadata to optionally pass to the putEventMetadata function.
    *
    * @param  imagePrefix Optional s3 prefix.
    * @param  numberOfPairs The length of the names and values arrays. 
    * @param  names The custom data names, to be paired with values, length must be <= MKV_MAX_TAG_NAME_LEN as defined in the native code.
    * @param  values The custom data values, to be paired with names, length must be <= MKV_MAX_TAG_VALUE_LEN as defined in the native code.
    */
    public StreamEventMetadata(String imagePrefix, int numberOfPairs, String[] names, String[] values) {
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

    public int getNumberOfPairs() {
        return mNumberOfPairs;
    }

    public String[] getNames() {
        return mNames;
    }
    
    public String[] getValues() {
        return mValues;
    }

}
