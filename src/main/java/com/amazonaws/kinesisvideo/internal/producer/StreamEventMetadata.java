package com.amazonaws.kinesisvideo.internal.producer;

import javax.annotation.Nullable;
import java.util.HashMap;

/**
 * Kinesis Video stream event metadata as provided by the native codebase.
 */
public class StreamEventMetadata {

    // Current class version as described by the native struct.
    public static final int STREAM_EVENT_METADATA_CURRENT_VERSION = 0;
    
    // Native MKV constants from libkvspic
    public static final int MKV_MAX_TAG_NAME_LEN = 128;
    public static final int MKV_MAX_TAG_VALUE_LEN = 256;


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
    private StreamEventMetadata(String imagePrefix, int numberOfPairs, String[] names, String[] values) {
        // Validate input parameters to prevent segmentation faults in JNI layer
        if (numberOfPairs > 0) {
            if (names == null) {
                throw new IllegalArgumentException("Names array cannot be null when numberOfPairs > 0");
            }
            if (values == null) {
                throw new IllegalArgumentException("Values array cannot be null when numberOfPairs > 0");
            }
            if (names.length < numberOfPairs) {
                throw new IllegalArgumentException("Names array length must be >= numberOfPairs");
            }
            if (values.length < numberOfPairs) {
                throw new IllegalArgumentException("Values array length must be >= numberOfPairs");
            }
            // Check for null strings within arrays
            for (int i = 0; i < numberOfPairs; i++) {
                if (names[i] == null) {
                    throw new IllegalArgumentException("Names array cannot contain null strings at index " + i);
                }
                if (values[i] == null) {
                    throw new IllegalArgumentException("Values array cannot contain null strings at index " + i);
                }
            }
        }
        
        mVersion = STREAM_EVENT_METADATA_CURRENT_VERSION;
        mImagePrefix = imagePrefix;
        mNumberOfPairs = numberOfPairs;
        mNames = names;
        mValues = values;
    }

    /**
     * Constructor chain for HashMap input:
     * 1. Public constructor validates and converts HashMap to arrays
     * 2. Helper function convertHashMap() does the conversion work
     * 3. Private constructor bridges to the main validation constructor
     * 4. Main private constructor performs final validation and sets fields
     */
    public StreamEventMetadata(String imagePrefix, HashMap<String, String> metadata) {
        this(imagePrefix, convertHashMap(metadata));
    }
    
    /**
     * Converts HashMap to arrays with null validation.
     * Returns ConvertedHashMap wrapper to pass multiple values to constructor.
     */
    private static ConvertedHashMap convertHashMap(HashMap<String, String> metadata) {
        HashMap<String, String> metadataCopy = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        
        for (String key : metadataCopy.keySet()) {
            if (key == null || metadataCopy.get(key) == null) {
                throw new IllegalArgumentException("HashMap cannot contain null keys or values");
            }
        }
        
        int mapSize = metadataCopy.size();
        String[] names = new String[mapSize];
        String[] values = new String[mapSize];
        
        int i = 0;
        for (HashMap.Entry<String, String> entry : metadataCopy.entrySet()) {
            names[i] = entry.getKey();
            values[i] = entry.getValue();
            i++;
        }
        
        return new ConvertedHashMap(mapSize, names, values);
    }
    
    /**
     * Bridge constructor that unpacks ConvertedHashMap and calls main validation constructor.
     */
    private StreamEventMetadata(String imagePrefix, ConvertedHashMap converted) {
        this(imagePrefix, converted.size, converted.names, converted.values);
    }
    
    /**
     * Simple wrapper class to return multiple values from convertHashMap().
     */
    private static class ConvertedHashMap {
        final int size;
        final String[] names;
        final String[] values;
        
        ConvertedHashMap(int size, String[] names, String[] values) {
            this.size = size;
            this.names = names;
            this.values = values;
        }
    }
    public StreamEventMetadata(HashMap<String, String> metadata) {
        this(null, metadata);
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
