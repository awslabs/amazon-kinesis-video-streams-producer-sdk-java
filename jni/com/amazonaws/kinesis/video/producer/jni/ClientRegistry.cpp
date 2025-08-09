#include "com/amazonaws/kinesis/video/producer/jni/ClientRegistry.h"

ClientRegistry& ClientRegistry::getInstance() {
    static ClientRegistry instance;
    return instance;
}

void ClientRegistry::addClient(KinesisVideoClientWrapper* client) {
    std::lock_guard<std::mutex> lock(mutex_);
    clients_.push_back(client);
}

void ClientRegistry::removeClient(KinesisVideoClientWrapper* client) {
    std::lock_guard<std::mutex> lock(mutex_);
    clients_.erase(std::remove(clients_.begin(), clients_.end(), client), clients_.end());
}

KinesisVideoClientWrapper* ClientRegistry::getFirstClient() {
    std::lock_guard<std::mutex> lock(mutex_);
    return clients_.empty() ? nullptr : clients_.front();
}
