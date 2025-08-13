#include "com/amazonaws/kinesis/video/producer/jni/ClientRegistry.h"

ClientRegistry& ClientRegistry::getInstance() {
    static ClientRegistry instance;
    return instance;
}

std::pair<size_t, int32_t> ClientRegistry::addClient(KinesisVideoClientWrapper* client) {
    std::lock_guard<std::mutex> lock(mutex_);
    clients_.push_back(client);
    totalClients++;
    return std::make_pair<>(clients_.size(), totalClients);
}

size_t ClientRegistry::removeClient(KinesisVideoClientWrapper* client) {
    std::lock_guard<std::mutex> lock(mutex_);
    clients_.erase(std::remove(clients_.begin(), clients_.end(), client), clients_.end());
    return clients_.size();
}

KinesisVideoClientWrapper* ClientRegistry::getFirstClient() {
    std::lock_guard<std::mutex> lock(mutex_);
    return clients_.empty() ? nullptr : clients_.front();
}
