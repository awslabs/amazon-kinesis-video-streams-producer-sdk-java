package com.amazonaws.kinesisvideo.internal.producer.jni;

import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Static helper for loading libraries
 */
@ThreadSafe
public class NativeLibraryLoader {

    private final Log mLog;

    public NativeLibraryLoader(final @Nonnull Log log) {
        mLog = Preconditions.checkNotNull(log);
    }

    /**
     * Attempts to load the native library by trying a library file first and if it fails then trying the library name
     *
     * @param fullPath    Full path to the library file (with or without the extension) - Optional
     * @param libraryName Library name - Optional
     * @return Whether the library was loaded OK
     */
    public boolean loadNativeLibrary(final String fullPath, final String libraryName) {
        // Both full path and library name can't be empty or null at the same time
        Preconditions.checkState(!((fullPath == null || fullPath.isEmpty()) && (libraryName == null || libraryName.isEmpty())),
                "Both the full path and library name can't be null at the same time");
        // Create the full names for different platforms
        final String soLibraryFileName = "lib" + libraryName + ".so";
        final String dylibLibraryFileName = "lib" + libraryName + ".dylib";
        final String dllLibraryFileName = "lib" + libraryName + ".dll";

        return loadNativeLibraryDirect(fullPath) ||
                loadNativeLibraryDirect(fullPath + ".so") ||
                loadNativeLibraryDirect(fullPath + ".dylib") ||
                loadNativeLibraryDirect(fullPath + ".dll") ||
                loadNativeLibraryDirect(soLibraryFileName) ||
                loadNativeLibraryDirect(dylibLibraryFileName) ||
                loadNativeLibraryDirect(dllLibraryFileName) ||
                loadNativeLibraryIndirect(libraryName);
    }

    /**
     * Attempts to load the native library directly given the path.
     *
     * @param libraryFullPath Full path to the library
     */
    private boolean loadNativeLibraryDirect(final @Nonnull String libraryFullPath) {
        if (libraryFullPath != null && !libraryFullPath.isEmpty()) {
            try {
                System.load(libraryFullPath);
                mLog.verbose("Success! Directly loaded native library %s.", libraryFullPath);
                return true;
            } catch (final UnsatisfiedLinkError e) {
                mLog.debug("Unsatisfied link error. Directly loading native library %s.", libraryFullPath);
            } catch (final SecurityException e) {
                mLog.debug("Security exception. Directly loading native library %s.", libraryFullPath);
            }
        }

        // This is the error return case.
        return false;
    }

    /**
     * Attempting to load the library by it's name
     *
     * @param libraryName Library name
     */
    private boolean loadNativeLibraryIndirect(final @Nonnull String libraryName) {
        try {
            System.loadLibrary(libraryName);
            mLog.verbose("Success! Indirectly loaded native library %s.", libraryName);
            return true;
        } catch (final UnsatisfiedLinkError e) {
            mLog.exception(e, "Unsatisfied link error. Loading native library %s failed with %s", libraryName, e.toString());
        } catch (final SecurityException e) {
            mLog.exception(e, "Security exception. Loading native library %s failed with %s", libraryName, e.toString());
        }

        // This is the error return case.
        return false;
    }
}
