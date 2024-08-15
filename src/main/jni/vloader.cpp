//
// Created by Judge on 12/23/2021.
//
#include <thread>
#include <string>
#include <errno.h>
#include <android/hardware_buffer.h>
#include <fcntl.h>
#include <unistd.h>
#include <OpenOVR/openxr_platform.h>
#include <jni.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>
#include "log.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl32.h>
#include <GLES2/gl2ext.h>
#include <assert.h>
#include <dlfcn.h>

static JavaVM* jvm;
XrInstanceCreateInfoAndroidKHR* OpenComposite_Android_Create_Info;
XrGraphicsBindingOpenGLESAndroidKHR* OpenComposite_Android_GLES_Binding_Info;

std::string (*OpenComposite_Android_Load_Input_File)(const char *path);

typedef struct native_handle
{
    int version;        /* sizeof(native_handle_t) */
    int numFds;         /* number of file-descriptors at &data[0] */
    int numInts;        /* number of ints at &data[numFds] */
#if defined(__clang__)
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wzero-length-array"
#endif
    int data[0];        /* numFds + numInts ints */
#if defined(__clang__)
#pragma clang diagnostic pop
#endif
} native_handle_t;

extern "C"
const native_handle_t* _Nullable AHardwareBuffer_getNativeHandle(
        const AHardwareBuffer* _Nonnull buffer);

extern "C"
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    if(jvm == nullptr) {
        jvm = vm;
    }
    return JNI_VERSION_1_4;
}

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
    OpenComposite_Android_Load_Input_File = load_file;

    env->GetJavaVM(&jvm);
    ctx = env->NewGlobalRef(ctx);
    OpenComposite_Android_Create_Info = new XrInstanceCreateInfoAndroidKHR{
            XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR,
            nullptr,
            jvm,
            ctx
    };

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
            jvm,
            ctx
    };

    res = initializeLoader((const XrLoaderInitInfoBaseHeaderKHR *) &loaderInitInfoAndroidKhr);
    if(!XR_SUCCEEDED(res)) {
        printf("xrInitializeLoaderKHR returned %d.\n", res);
    }
}

EGLDisplay xrEglDisplay;
EGLConfig xrConfig;
EGLSurface xrEglSurface;
EGLContext xrEglContext;

typedef EGLDisplay eglGetDisplay_t (EGLNativeDisplayType display_id);
typedef EGLBoolean eglInitialize_t (EGLDisplay dpy, EGLint *major, EGLint *minor);
typedef EGLBoolean eglChooseConfig_t (EGLDisplay dpy, const EGLint *attrib_list, EGLConfig *configs, EGLint config_size, EGLint *num_config);
typedef EGLBoolean eglGetConfigAttrib_t (EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint *value);
typedef EGLBoolean eglBindAPI_t (EGLenum api);
typedef EGLSurface eglCreatePbufferSurface_t (EGLDisplay dpy, EGLConfig config, const EGLint *attrib_list);
typedef EGLContext eglCreateContext_t (EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint *attrib_list);
typedef EGLBoolean eglMakeCurrent_t (EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx);
typedef EGLImage eglCreateImage_t (EGLDisplay dpy, EGLContext ctx, EGLenum target, EGLClientBuffer buffer, const EGLAttrib *attrib_list);
typedef EGLint eglGetError_t (void);
typedef __eglMustCastToProperFunctionPointerType eglGetProcAddress_t (const char *procname);

static eglGetDisplay_t* eglGetDisplay_p;
static eglInitialize_t* eglInitialize_p;
static eglChooseConfig_t* eglChooseConfig_p;
static eglGetConfigAttrib_t* eglGetConfigAttrib_p;
static eglBindAPI_t* eglBindAPI_p;
static eglCreatePbufferSurface_t* eglCreatePbufferSurface_p;
static eglCreateContext_t* eglCreateContext_p;
static eglMakeCurrent_t* eglMakeCurrent_p;
static eglCreateImage_t* eglCreateImage_p;
static eglGetProcAddress_t* eglGetProcAddress_p;
static eglGetError_t* eglGetError_p;
static PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC eglGetNativeClientBufferANDROID_p;

void dlsym_egl() {
    void* handle = dlopen("libEGL.so", RTLD_NOW);
    eglGetDisplay_p = (eglGetDisplay_t*) dlsym(handle, "eglGetDisplay");
    eglInitialize_p = (eglInitialize_t*) dlsym(handle, "eglInitialize");
    eglChooseConfig_p = (eglChooseConfig_t*) dlsym(handle, "eglChooseConfig");
    eglGetConfigAttrib_p = (eglGetConfigAttrib_t*) dlsym(handle, "eglGetConfigAttrib");
    eglBindAPI_p = (eglBindAPI_t*) dlsym(handle, "eglBindAPI");
    eglCreatePbufferSurface_p = (eglCreatePbufferSurface_t*) dlsym(handle, "eglCreatePbufferSurface");
    eglCreateContext_p = (eglCreateContext_t*) dlsym(handle, "eglCreateContext");
    eglMakeCurrent_p = (eglMakeCurrent_t*) dlsym(handle, "eglMakeCurrent");
    eglCreateImage_p = (eglCreateImage_t*) dlsym(handle, "eglCreateImage");
    eglGetError_p = (eglGetError_t*) dlsym(handle, "eglGetError");
    eglGetProcAddress_p = (eglGetProcAddress_t*) dlsym(handle, "eglGetProcAddress");
    eglGetNativeClientBufferANDROID_p = (PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC) eglGetProcAddress_p("eglGetNativeClientBufferANDROID");
}

EGLAPI __eglMustCastToProperFunctionPointerType EGLAPIENTRY eglGetProcAddress(const char *procname) {
    if(eglGetProcAddress_p == nullptr) {
        dlsym_egl();
    }
    return eglGetProcAddress_p(procname);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_vivecraft_util_VLoader_setEGLGlobal(JNIEnv* env, jclass clazz) {
    if(eglGetDisplay_p != nullptr) {
        return;
    }

    dlsym_egl();

    xrEglDisplay = eglGetDisplay_p(EGL_DEFAULT_DISPLAY);
    if (xrEglDisplay == EGL_NO_DISPLAY) {
        printf("XREGLBridge: Error eglGetDefaultDisplay() failed: %d\n", eglGetError_p());
    }

    printf("XREGLBridge: Initializing\n");
    if (!eglInitialize_p(xrEglDisplay, NULL, NULL)) {
        printf("XREGLBridge: Error eglInitialize() failed: %d\n", eglGetError_p());
    }

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

    EGLint num_configs;
    EGLint vid;

    if (!eglChooseConfig_p(xrEglDisplay, attribs, &xrConfig, 1, &num_configs)) {
        printf("XREGLBridge: Error couldn't get an EGL visual config: %d\n", eglGetError_p());
    }

    assert(xrConfig);
    assert(num_configs > 0);

    if (!eglGetConfigAttrib_p(xrEglDisplay, xrConfig, EGL_NATIVE_VISUAL_ID, &vid)) {
        printf("XREGLBridge: Error eglGetConfigAttrib() failed: %d\n", eglGetError_p());
    }

    if(!eglBindAPI_p(EGL_OPENGL_ES_API)) {
        printf("XREGLBridge: Error eglBindAPI() failed: %d\n", eglGetError_p());
    }

    xrEglSurface = eglCreatePbufferSurface_p(xrEglDisplay, xrConfig,
                                             nullptr);
    if (!xrEglSurface) {
        printf("XREGLBridge: Error eglCreatePbufferSurface failed: %d\n", eglGetError_p());
    }

    printf("XREGLBridge: Initialized!\n");
    printf("XREGLBridge: ThreadID=%d\n", gettid());
    printf("XREGLBridge: XREGLDisplay=%p, XREGLSurface=%p\n",
/* window==0 ? EGL_NO_CONTEXT : */
           xrEglDisplay,
           xrEglSurface
    );

    const EGLint ctx_attribs[] = {
            EGL_CONTEXT_CLIENT_VERSION , 3,
            EGL_NONE
    };

    xrEglContext = eglCreateContext_p(xrEglDisplay, xrConfig, nullptr, ctx_attribs);
    if(xrEglContext == EGL_NO_CONTEXT) {
        printf("XREGLBridge: eglCreateContext call failed, returned %d\n", eglGetError_p());
    }

    EGLBoolean success = eglMakeCurrent_p(
            xrEglDisplay,
            xrEglSurface,
            xrEglSurface,
            xrEglContext
    );
    if (success == EGL_FALSE) {
        printf("XREGLBridge: Error: eglMakeCurrent() failed: %d\n", eglGetError_p());
    } else {
        printf("XREGLBridge: eglMakeCurrent() succeed!\n");
    }

    OpenComposite_Android_GLES_Binding_Info = new XrGraphicsBindingOpenGLESAndroidKHR {
            XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR,
            nullptr,
            (void*)xrEglDisplay,
            (void*) xrConfig,
            (void*)xrEglContext
    };
}

int getDMABuf(AHardwareBuffer* buf) {
    const native_handle_t* handle = AHardwareBuffer_getNativeHandle(buf);
    int dma_buf = (handle && handle->numFds) ? handle->data[0] : -1;
    return dma_buf;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_org_vivecraft_util_VLoader_getImage(JNIEnv* env, jclass clazz, jint width, jint height) {
    if(eglGetDisplay_p == nullptr) {
        dlsym_egl();
    }

    AHardwareBuffer * buf;
    AHardwareBuffer_Desc* desc = new AHardwareBuffer_Desc {
            static_cast<uint32_t>(width),
            static_cast<uint32_t>(height),
            1,
            AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM,
            AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER
    };
    AHardwareBuffer_allocate(desc, &buf);

    EGLImage img = eglCreateImage_p(xrEglDisplay,
                                    EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, eglGetNativeClientBufferANDROID_p(buf),
                                    nullptr);
    GLuint tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, img);

    jintArray data = env->NewIntArray(2);
    jint intBuf[2] = {
            static_cast<jint>(tex),
            reinterpret_cast<jint>(getDMABuf(buf))
    };
    env->SetIntArrayRegion(data, 0, 2, intBuf);
    return data;
}