package com.amazonaws.kinesisvideo.producer;

import javax.annotation.Nonnull;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;

/**
 * Kinesis Video Tag representation.
 *
 * NOTE: This class must match the Tag declaration in native code in
 * /client/Include.h
 *
 */
public class Tag {
    /**
     * Tag name
     */
    private final String mName;

    /**
     * Tag value
     */
    private final String mValue;

    /**
     * Public constructor which can be called from native code.
     * @param name Name of the tag
     * @param value Value of the tag
     */
    public Tag(@Nonnull final String name, @Nonnull final String value) {
        mName = Preconditions.checkNotNull(name);
        mValue = Preconditions.checkNotNull(value);
    }

    /**
     * Gets the name of the tag
     * @return tag name
     */
    @Nonnull
    public String getName() {
        return mName;
    }

    /**
     * Gets the value of the tag
     * @return tag value
     */
    @Nonnull
    public String getValue() {
        return mValue;
    }
}
