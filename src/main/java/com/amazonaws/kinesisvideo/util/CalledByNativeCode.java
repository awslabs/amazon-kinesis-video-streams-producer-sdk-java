package com.amazonaws.kinesisvideo.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to mark methods (and constructors) that are called by the JNI layer (native codebase)
 */
@Documented // Show up in the Javadoc when this annotation is used
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
public @interface CalledByNativeCode {
}
