//
// Created by Judge on 12/23/2021.
//
#include <thread>
#include <string>
#include <errno.h>
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
static eglGetProcAddress_t* eglGetProcAddress_p;
static eglGetError_t* eglGetError_p;
static PFNGLCREATEMEMORYOBJECTSEXTPROC glCreateMemoryObjectsEXT_p;
static PFNGLIMPORTMEMORYFDEXTPROC glImportMemoryFdEXT_p;
static PFNGLTEXSTORAGEMEM2DEXTPROC glTexStorageMem2DEXT_p;

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
    eglGetError_p = (eglGetError_t*) dlsym(handle, "eglGetError");
    eglGetProcAddress_p = (eglGetProcAddress_t*) dlsym(handle, "eglGetProcAddress");

    glCreateMemoryObjectsEXT_p = (PFNGLCREATEMEMORYOBJECTSEXTPROC) eglGetProcAddress_p("glCreateMemoryObjectsEXT");
    glImportMemoryFdEXT_p = (PFNGLIMPORTMEMORYFDEXTPROC) eglGetProcAddress_p("glImportMemoryFdEXT");
    glTexStorageMem2DEXT_p = (PFNGLTEXSTORAGEMEM2DEXTPROC) eglGetProcAddress_p("glTexStorageMem2DEXT");
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

extern "C"
JNIEXPORT jint JNICALL
Java_org_vivecraft_util_VLoader_getImage(JNIEnv* env, jclass clazz, jint fd, jint width, jint height) {
    if(eglGetDisplay_p == nullptr) {
        dlsym_egl();
    }

    GLuint tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    GLuint memory;
    glCreateMemoryObjectsEXT_p(1, &memory);
    glImportMemoryFdEXT_p(memory, width * height * 4, GL_HANDLE_TYPE_OPAQUE_FD_EXT, fd);
    glTexStorageMem2DEXT_p(GL_TEXTURE_2D, 1, GL_RGBA8, width, height, memory,  0);

    GLint error = eglGetError_p();
    while(error != EGL_SUCCESS) {
        printf("Failed to bind texture! %d\n", error);
        error = eglGetError_p();
    }

    return tex;
}