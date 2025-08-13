package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.util.CalledByNativeCode;

import javax.annotation.Nonnull;

/**
 * Kinesis Video Tag representation.
 * <p>
 * NOTE: This class must match the Tag declaration in native code in
 * /client/Include.h
 */
public class Tag {
    /**
     * Tag name
     */
    @Nonnull
    private final String name;

    /**
     * Tag value
     */
    @Nonnull
    private final String value;

    /**
     * Public constructor which can be called from native code.
     *
     * @param name  Name of the tag
     * @param value Value of the tag
     */
    @CalledByNativeCode
    public Tag(@Nonnull final String name, @Nonnull final String value) {
        this.name = Preconditions.checkNotNull(name);
        this.value = Preconditions.checkNotNull(value);
    }

    /**
     * Gets the name of the tag
     *
     * @return tag name
     */
    @Nonnull
    @CalledByNativeCode
    public String getName() {
        return this.name;
    }

    /**
     * Gets the value of the tag
     *
     * @return tag value
     */
    @Nonnull
    @CalledByNativeCode
    public String getValue() {
        return this.value;
    }

    /**
     * @return a string representation of this
     */
    @Override
    public String toString() {
        return "Tag{name=" + this.name + ", value=" + this.value + "}";
    }
}
