#include "com/amazonaws/kinesis/video/producer/jni/ClientRegistry.h"

ClientRegistry& ClientRegistry::getInstance() {
    static ClientRegistry instance;
    return instance;
}

std::pair<SIZE_T, UINT32> ClientRegistry::addClient(KinesisVideoClientWrapper* client) {
    std::lock_guard<std::mutex> lock(mutex_);
    clients_.push_back(client);
    totalClients++;
    return std::make_pair<>(clients_.size(), totalClients);
}

SIZE_T ClientRegistry::removeClient(KinesisVideoClientWrapper* client) {
    std::lock_guard<std::mutex> lock(mutex_);
    clients_.erase(std::remove(clients_.begin(), clients_.end(), client), clients_.end());
    return clients_.size();
}

KinesisVideoClientWrapper* ClientRegistry::getFirstClient() {
    std::lock_guard<std::mutex> lock(mutex_);
    return clients_.empty() ? nullptr : clients_.front();
}

void ClientRegistry::withFirstClient(const std::function<void(KinesisVideoClientWrapper*)>& func) {
    std::lock_guard<std::mutex> lock(mutex_);
    KinesisVideoClientWrapper* client = clients_.empty() ? nullptr : clients_.front();
    if (client) {
        func(client);
    }
}

