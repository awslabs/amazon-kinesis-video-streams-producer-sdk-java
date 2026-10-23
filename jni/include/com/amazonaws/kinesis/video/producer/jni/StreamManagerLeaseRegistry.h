/**
 * Tracks live KinesisVideoClientWrapper objects so that a JNI call can never use a wrapper that
 * freeKinesisVideoClient() is deleting or has already deleted.
 *
 * Every JNI entry point pins the wrapper with a StreamManagerLease for the duration of the call.
 * freeKinesisVideoClient() first marks the handle as closing (new calls get NULL and return), waits for
 * the pinned calls to drain, and only then deletes the wrapper and the SyncMutex inside it.
 */

#ifndef __STREAM_MANAGER_LEASE_REGISTRY_H__
#define __STREAM_MANAGER_LEASE_REGISTRY_H__

#pragma once

#include <chrono>
#include <condition_variable>
#include <mutex>
#include <unordered_map>

#include "KinesisVideoClientWrapper.h"

// How long freeKinesisVideoClient() waits for in-flight calls before giving up and leaking the wrapper
static const std::chrono::seconds gStreamManagerDrainTimeout(30);

class StreamManagerLeaseRegistry
{
    struct Entry
    {
        UINT32 inFlightCalls;
        BOOL closing;
    };

    // Function-local statics keep this header-only without a new translation unit
    static std::mutex& lock()
    {
        static std::mutex sLock;
        return sLock;
    }

    static std::condition_variable& drained()
    {
        static std::condition_variable sDrained;
        return sDrained;
    }

    static std::unordered_map<jlong, Entry>& entries()
    {
        static std::unordered_map<jlong, Entry> sEntries;
        return sEntries;
    }

public:
    static void add(jlong handle)
    {
        std::lock_guard<std::mutex> guard(lock());
        Entry entry = {0, FALSE};
        entries()[handle] = entry;
    }

    // Returns the wrapper and counts the call as in flight, or NULL if the handle is unknown or being freed.
    static KinesisVideoClientWrapper* acquire(jlong handle)
    {
        std::lock_guard<std::mutex> guard(lock());
        std::unordered_map<jlong, Entry>::iterator it = entries().find(handle);
        if (it == entries().end() || it->second.closing) {
            return NULL;
        }

        it->second.inFlightCalls++;
        return FROM_WRAPPER_HANDLE(handle);
    }

    static void release(jlong handle)
    {
        std::lock_guard<std::mutex> guard(lock());
        std::unordered_map<jlong, Entry>::iterator it = entries().find(handle);
        if (it != entries().end() && --it->second.inFlightCalls == 0 && it->second.closing) {
            drained().notify_all();
        }
    }

    // Stops new calls from pinning the handle and waits for the in-flight ones to return.
    // Returns FALSE if the handle is unknown, already being freed, or the calls did not drain in time.
    // The caller must not delete the wrapper in that case.
    static BOOL drainAndRemove(jlong handle)
    {
        std::unique_lock<std::mutex> guard(lock());
        std::unordered_map<jlong, Entry>::iterator it = entries().find(handle);
        if (it == entries().end() || it->second.closing) {
            return FALSE;
        }

        it->second.closing = TRUE;

        // Look the entry up again on every wakeup: add() on another thread can rehash the map
        bool drainedInTime = drained().wait_for(guard, gStreamManagerDrainTimeout, [handle] {
            return entries()[handle].inFlightCalls == 0;
        });

        if (!drainedInTime) {
            // Keep the entry in the closing state so release() keeps working. The wrapper is leaked, which is
            // better than freeing a mutex another thread is still holding.
            return FALSE;
        }

        entries().erase(handle);
        return TRUE;
    }
};

/**
 * Pins a client wrapper for the lifetime of this object. Declare it before any SyncMutex::Autolock on the
 * wrapper so that the lock is released before the pin is.
 */
class StreamManagerLease
{
    jlong mHandle;
    KinesisVideoClientWrapper* mWrapper;

    StreamManagerLease(const StreamManagerLease&); // Prevent copies
    StreamManagerLease& operator=(const StreamManagerLease&); // Prevent assignment

public:
    explicit StreamManagerLease(jlong handle) : mHandle(handle),
            mWrapper(StreamManagerLeaseRegistry::acquire(handle)) {}

    ~StreamManagerLease()
    {
        if (mWrapper != NULL) {
            StreamManagerLeaseRegistry::release(mHandle);
        }
    }

    KinesisVideoClientWrapper* get() const
    {
        return mWrapper;
    }
};

#endif // __STREAM_MANAGER_LEASE_REGISTRY_H__
