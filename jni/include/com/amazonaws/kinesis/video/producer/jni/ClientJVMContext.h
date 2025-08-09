#pragma once

#include <jni.h>

struct ClientJVMContext {
    JavaVM* jvm;
    jobject javaObjectRef;          // Pointer to NativeKinesisVideoProducerJni java object
    jmethodID logPrintMethodId;     // Pointer to NativeKinesisVideoProducerJni's 'logPrint' method

    ClientJVMContext() : jvm(nullptr), javaObjectRef(nullptr), logPrintMethodId(nullptr) {}

    ~ClientJVMContext() {
        if (jvm != NULL && javaObjectRef != NULL) {
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
