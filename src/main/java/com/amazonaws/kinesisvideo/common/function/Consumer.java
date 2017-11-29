package com.amazonaws.kinesisvideo.common.function;

public interface Consumer<T> {
    void accept(final T object);
}
