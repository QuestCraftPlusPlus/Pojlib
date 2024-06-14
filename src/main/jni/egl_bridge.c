#include <jni.h>
#include <assert.h>
#include <dlfcn.h>

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <unistd.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>

#ifdef GLES_TEST
#include <GLES2/gl2.h>
#endif

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/rect.h>
#include <string.h>
#include <pthread.h>
#include "utils.h"
#include "GL/gl.h"
#include "adrenotools/driver.h"

EGLContext gameEglContext;
EGLDisplay gameEglDisplay;
EGLSurface gameEglSurface;
EGLConfig gameConfig;

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
typedef EGLBoolean eglSwapBuffers_t (EGLDisplay dpy, EGLSurface surface);
typedef EGLBoolean eglSwapInterval_t (EGLDisplay dpy, EGLint interval);
typedef __eglMustCastToProperFunctionPointerType eglGetProcAddress_t (const char *procname);

eglGetDisplay_t* eglGetDisplay_p;
eglInitialize_t* eglInitialize_p;
eglChooseConfig_t* eglChooseConfig_p;
eglGetConfigAttrib_t* eglGetConfigAttrib_p;
eglBindAPI_t* eglBindAPI_p;
eglCreatePbufferSurface_t* eglCreatePbufferSurface_p;
eglCreateContext_t* eglCreateContext_p;
eglMakeCurrent_t* eglMakeCurrent_p;
eglCreateImage_t* eglCreateImage_p;
eglGetError_t* eglGetError_p;
eglSwapBuffers_t* eglSwapBuffers_p;
eglSwapInterval_t* eglSwapInterval_p;
eglGetProcAddress_t* eglGetProcAddress_p;

void* gbuffer;

void dlsym_egl() {
    void* handle = dlopen("libEGL_mesa.so", RTLD_NOW);
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
    eglSwapBuffers_p = (eglSwapBuffers_t*) dlsym(handle, "eglSwapBuffers");
    eglSwapInterval_p = (eglSwapInterval_t*) dlsym(handle, "eglSwapInterval");
    eglGetProcAddress_p = (eglGetProcAddress_t*) dlsym(handle, "eglGetProcAddress");
}

void pojav_openGLOnLoad() {
}
void pojav_openGLOnUnload() {

}

void pojavTerminate() {
}


void* pojavGetCurrentContext() {
    return gameEglContext;
}

void error_callback(
        EGLenum error,
        const char *command,
        EGLint messageType,
        EGLLabelKHR threadLabel,
        EGLLabelKHR objectLabel,
        const char* message) {
    printf("EGL Debug Callback: Error %d\n", error);
    printf("EGL Debug Callback: Command %s\n", command);
    printf("EGL Debug Callback: Message %s\n", message);
}

int gameEglInit() {
    if(eglGetDisplay_p == NULL) {
        dlsym_egl();
    }

    PFNEGLDEBUGMESSAGECONTROLKHRPROC eglDebugMessageControlKHR_p;
    eglDebugMessageControlKHR_p = (PFNEGLDEBUGMESSAGECONTROLKHRPROC) eglGetProcAddress_p("eglDebugMessageControlKHR");
    if(eglDebugMessageControlKHR_p) {
        eglDebugMessageControlKHR_p(error_callback, NULL);
    }

    if (gameEglDisplay == NULL || gameEglDisplay == EGL_NO_DISPLAY) {
        gameEglDisplay = eglGetDisplay_p(EGL_DEFAULT_DISPLAY);
        if (gameEglDisplay == EGL_NO_DISPLAY) {
            printf("GameEGL: Error eglGetDefaultDisplay() failed: %d\n", eglGetError_p());
        }
    }

    printf("GameEGL: Initializing\n");
    // printf("EGLBridge: ANativeWindow pointer = %p\n", androidWindow);
    //(*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/Exception"),"Trace exception");
    if (!eglInitialize_p(gameEglDisplay, NULL, NULL)) {
        printf("GameEGL: Error eglInitialize() failed: %d\n", eglGetError_p());
    }

    static const EGLint attribs[] = {
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            // Minecraft required on initial 24
            EGL_DEPTH_SIZE, 24,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT,
            EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
            EGL_NONE
    };

    EGLint num_configs;
    EGLint vid;

    if (!eglChooseConfig_p(gameEglDisplay, attribs, &gameConfig, 1, &num_configs)) {
        printf("GameEGL: Error couldn't get an EGL visual config: %d\n", eglGetError_p());
    }

    assert(gameConfig);
    assert(num_configs > 0);

    if (!eglGetConfigAttrib_p(gameEglDisplay, gameConfig, EGL_NATIVE_VISUAL_ID, &vid)) {
        printf("EGLBridge: Error eglGetConfigAttrib() failed: %d\n", eglGetError_p());
    }

    if(!eglBindAPI_p(EGL_OPENGL_API)) {
        printf("GameEGL: Error eglBindAPI() failed: %d\n", eglGetError_p());
    }

    static const EGLint surface_attribs[] = {
            EGL_HEIGHT, 1,
            EGL_WIDTH, 1,
            EGL_NONE
    };

    gameEglSurface = eglCreatePbufferSurface_p(gameEglDisplay, gameConfig,
                                           surface_attribs);
    if (!gameEglSurface) {
        printf("GameEGL: Error eglCreatePbufferSurface failed: %d\n", eglGetError_p());
    }

    printf("GameEGL: Initialized!\n");
    printf("GameEGL: ThreadID=%d\n", gettid());
    printf("GameEGL: gameEGLDisplay=%p, gameEGLSurface=%p\n",
/* window==0 ? EGL_NO_CONTEXT : */
           gameEglDisplay,
           gameEglSurface
    );

    return 1;
}

void initDriver() {
    char *gpuStuff;
    char *nativeDir;
    asprintf(&nativeDir, "%s/", getenv("POJLIB_NATIVEDIR"));
    asprintf(&gpuStuff, "%s/gpustuff", getenv("HOME"));
    void *libvulkan = adrenotools_open_libvulkan(RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM, gpuStuff,
                                                 gpuStuff, nativeDir,
                                                 "libvulkan_freedreno.so", NULL, NULL);
    dlopen("libvulkan.so", RTLD_NOW);
    printf("Driver Loader: libvulkan handle: %p\n", libvulkan);
    char *vulkanPtrString;
    asprintf(&vulkanPtrString, "%p", libvulkan);
    setenv("VULKAN_PTR", vulkanPtrString, 1);
}

int pojavInit() {
    savedWidth = 1;
    savedHeight = 1;

    initDriver();
    gameEglInit();
}

void pojavSetWindowHint(int hint, int value) {
    // Stub
}

void pojavPumpEvents(void* window) {
    // Stub
}

int32_t stride;
void pojavSwapBuffers() {
    eglSwapBuffers_p(gameEglDisplay, gameEglSurface);
}

bool locked = false;
void pojavMakeCurrent(void* window) {
    EGLBoolean success = eglMakeCurrent_p(
            gameEglDisplay,
            gameEglSurface,
            gameEglSurface,
            window
    );

    gameEglContext = window;

    if (success == EGL_FALSE) {
        printf("GameEGL: Error: eglMakeCurrent() failed: %d\n", eglGetError_p());
    } else {
        printf("GameEGL: eglMakeCurrent() succeed!\n");
    }
}

JNIEXPORT JNICALL jlong
Java_pojlib_util_JREUtils_getEGLDisplayPtr(JNIEnv *env, jclass clazz) {
    return (jlong) &gameEglDisplay;
}

JNIEXPORT JNICALL jlong
Java_pojlib_util_JREUtils_getEGLContextPtr(JNIEnv *env, jclass clazz) {
    return (jlong) &gameEglContext;
}

JNIEXPORT JNICALL jlong
Java_pojlib_util_JREUtils_getEGLConfigPtr(JNIEnv *env, jclass clazz) {
    return (jlong) &gameConfig;
}

void* pojavCreateContext(void* contextSrc) {
    const EGLint ctx_attribs[] = {
            EGL_CONTEXT_MAJOR_VERSION, 3,
            EGL_CONTEXT_MINOR_VERSION, 2,
            EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT,
            EGL_NONE
    };
    EGLContext* ctx = eglCreateContext_p(gameEglDisplay, gameConfig, contextSrc, ctx_attribs);
    if(ctx == EGL_NO_CONTEXT) {
        printf("GameEGL: eglCreateContext call failed, returned %d\n", eglGetError_p());
    }

    printf("GameEGL: Context %p\n", ctx);
    return ctx;
}

JNIEXPORT JNICALL jlong
Java_org_lwjgl_opengl_GL_getGraphicsBufferAddr(JNIEnv *env, jobject thiz) {
    return (jlong) &gbuffer;
}
JNIEXPORT JNICALL jintArray
Java_org_lwjgl_opengl_GL_getNativeWidthHeight(JNIEnv *env, jobject thiz) {
    jintArray ret = (*env)->NewIntArray(env,2);
    jint arr[] = {savedWidth, savedHeight};
    (*env)->SetIntArrayRegion(env,ret,0,2,arr);
    return ret;
}
void pojavSwapInterval(int interval) {
    eglSwapInterval_p(gameEglDisplay, interval);
}
