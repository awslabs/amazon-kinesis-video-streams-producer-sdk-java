#pragma once

#include <algorithm>
#include <vector>
#include <mutex>

class KinesisVideoClientWrapper;

/**
 * Thread-safe registry for managing active KinesisVideoClientWrapper instances.
 * All accesses to the client list are properly synchronized.
 */
class ClientRegistry {
public:
    /// Returns the singleton instance
    static ClientRegistry& getInstance();

    /// Thread-safe addition of a client to the registry
    void addClient(KinesisVideoClientWrapper* client);

    /// Thread-safe removal of a client from the registry
    void removeClient(KinesisVideoClientWrapper* client);

    /// Thread-safe retrieval of the first client, returns nullptr if empty
    KinesisVideoClientWrapper* getFirstClient();

private:
    ClientRegistry() = default;
    std::vector<KinesisVideoClientWrapper*> clients_;
    std::mutex mutex_;  ///< Protects access to clients_ vector
};
