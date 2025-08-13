#pragma once

#include <jni.h>

#include <com/amazonaws/kinesis/video/client/Include.h>

struct ClientJVMContext {
    JavaVM* jvm;
    jobject javaObjectRef;          // Pointer to NativeKinesisVideoProducerJni java object
    jmethodID logPrintMethodId;     // Pointer to NativeKinesisVideoProducerJni's 'logPrint' method
    UINT32 clientId;                // Monotonically increasing client identifier

    ClientJVMContext() : jvm(nullptr), javaObjectRef(nullptr), logPrintMethodId(nullptr) {
        clientId = 0;
    }

    ~ClientJVMContext() {
        if (jvm != NULL && javaObjectRef != NULL) {
            DLOGD("Deleting client #%d", clientId);
            JNIEnv* env;
            if (jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
                env->DeleteGlobalRef(javaObjectRef);
            }
            javaObjectRef = NULL;
        }
    }

    ClientJVMContext(const ClientJVMContext&) = delete;
    ClientJVMContext& operator=(const ClientJVMContext&) = delete;
};
