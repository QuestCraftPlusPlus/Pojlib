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
#include "log.h"

std::string (*OpenComposite_Android_Load_Input_File)(const char *path);

static std::string load_file(const char *path) {
    // Just read the file from the filesystem, we changed the working directory earlier so
    // Vivecraft can extract it's manifest files.

    printf("Path: %s\n", path);
    int fd = open(path, O_RDONLY);
    if (!fd) {
        LOGE("Failed to load manifest file %s: %d %s\n", path, errno, strerror(errno));
    }

    int length = lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);

    std::string data;
    data.resize(length);
    if (!read(fd, (void *) data.data(), data.size())) {
        LOGE("Failed to load manifest file %s failed to read: %d %s\n", path, errno, strerror(errno));
    }

    if (close(fd)) {
        LOGE("Failed to load manifest file %s failed to close: %d %s\n", path, errno,
             strerror(errno));
    }

    return std::move(data);
}

extern "C"
JNIEXPORT void JNICALL
Java_pojlib_util_VLoader_setAndroidInitInfo(JNIEnv *env, jclass clazz, jobject ctx) {
    pojav_environ->activity = env->NewGlobalRef(ctx);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_vivecraft_util_VLoader_setEGLGlobal(JNIEnv* env, jclass clazz) {
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
    pojav_environ->OpenComposite_Android_GLES_Binding_Info = new XrGraphicsBindingOpenGLESAndroidKHR {
            XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR,
            nullptr,
            (void*)eglGetCurrentDisplay(),
            (void*) cfg,
            (void*)eglGetCurrentContext()
    };
}