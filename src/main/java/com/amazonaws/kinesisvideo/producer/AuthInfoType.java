package com.amazonaws.kinesisvideo.producer;

/**
 * Definition of the Auth Info Type.
 *
 * NOTE: This enum must be the same as defined in /client/Include.h
 *
 */

public enum AuthInfoType {

    /**
     * Auth info undefined or not specified. Used as a sentinel
     */
    UNDEFINED(0),

    /**
     * Certificate authentication
     */
    CERT(1),

    /**
     * Security Token integration
     */
    SECURITY_TOKEN(2),

    /**
     * No authentication is required.
     */
    NONE(3);

    private final int mType;

    AuthInfoType(int type) {
        mType = type;
    }

    /**
     * Returns the type as an integer which can be consumed by the native layer
     */
    public int getIntType() {
        return mType;
    }
}