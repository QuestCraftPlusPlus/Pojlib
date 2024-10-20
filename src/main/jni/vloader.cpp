//
// Created by Judge on 12/23/2021.
//
#include <thread>
#include <string>
#include <errno.h>
#include <android/hardware_buffer.h>
#include <fcntl.h>
#include <unistd.h>
#include <jni.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>
#include <environ/environ.h>
#include <GLES3/gl32.h>
#include <EGL/egl.h>
#include <openxr/openxr.h>
#include "log.h"

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_util_VLoader_getEGLGlobal(JNIEnv* env, jclass clazz) {
    if(pojav_environ->XRAndroidGLESBinding == nullptr) {
        EGLConfig cfg;
        EGLint num_configs;

        static const EGLint attribs[] = {
                EGL_RED_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                // Minecraft required on initial 24
                EGL_DEPTH_SIZE, 24,
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                EGL_NONE
        };

        eglChooseConfig(eglGetCurrentDisplay(), attribs, &cfg, 1, &num_configs);
        pojav_environ->XRAndroidGLESBinding = new XrGraphicsBindingOpenGLESAndroidKHR{
                XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR,
                nullptr,
                (void *) eglGetCurrentDisplay(),
                (void *) cfg,
                (void *) eglGetCurrentContext()
        };
    }

    return reinterpret_cast<jlong>(pojav_environ->XRAndroidGLESBinding);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_util_VLoader_getAndroidCreateInfo(JNIEnv* env, jclass clazz) {
    PFN_xrInitializeLoaderKHR initializeLoader = nullptr;
    XrResult res;

    res = xrGetInstanceProcAddr(XR_NULL_HANDLE, "xrInitializeLoaderKHR",
                                (PFN_xrVoidFunction *) (&initializeLoader));

    if(!XR_SUCCEEDED(res)) {
        printf("xrGetInstanceProcAddr returned %d.\n", res);
    }

    XrLoaderInitInfoAndroidKHR loaderInitInfoAndroidKhr = {
            XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR,
            nullptr,
            (void*) pojav_environ->dalvikJavaVMPtr,
            (void*) pojav_environ->activity
    };

    res = initializeLoader((const XrLoaderInitInfoBaseHeaderKHR *) &loaderInitInfoAndroidKhr);
    if(!XR_SUCCEEDED(res)) {
        printf("xrInitializeLoaderKHR returned %d.\n", res);
    }

    JNIEnv* newEnv;
    pojav_environ->dalvikJavaVMPtr->AttachCurrentThread(&newEnv, NULL);

    return reinterpret_cast<jlong>(pojav_environ->XRAndroidInstanceBinding);
}
extern "C"
JNIEXPORT void JNICALL
Java_pojlib_util_VLoader_setAndroidInitInfo(JNIEnv *env, jclass clazz, jobject ctx) {
    pojav_environ->activity = env->NewGlobalRef(ctx);
}