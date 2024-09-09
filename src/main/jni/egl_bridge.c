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

typedef EGLDisplay eglGetDisplay_t (EGLNativeDisplayType display_id);
typedef EGLBoolean eglInitialize_t (EGLDisplay dpy, EGLint *major, EGLint *minor);
typedef EGLBoolean eglChooseConfig_t (EGLDisplay dpy, const EGLint *attrib_list, EGLConfig *configs, EGLint config_size, EGLint *num_config);
typedef EGLBoolean eglGetConfigAttrib_t (EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint *value);
typedef EGLBoolean eglBindAPI_t (EGLenum api);
typedef EGLSurface eglCreatePbufferSurface_t (EGLDisplay dpy, EGLConfig config, const EGLint *attrib_list);
typedef EGLContext eglCreateContext_t (EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint *attrib_list);
typedef EGLBoolean eglMakeCurrent_t (EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx);
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
eglGetError_t* eglGetError_p;
eglSwapBuffers_t* eglSwapBuffers_p;
eglSwapInterval_t* eglSwapInterval_p;
eglGetProcAddress_t* eglGetProcAddress_p;

EGLContext xrEglContext;
EGLDisplay xrEglDisplay;
EGLSurface xrEglSurface;
EGLConfig xrConfig;

void* gbuffer;

void pojav_openGLOnLoad() {
}
void pojav_openGLOnUnload() {

}

void pojavTerminate() {
}

void dlsym_egl() {
    void* handle = dlopen("libtinywrapper.so", RTLD_NOW);
    eglGetProcAddress_p = (eglGetProcAddress_t*) dlsym(handle, "eglGetProcAddress");
    eglGetDisplay_p = (eglGetDisplay_t*) eglGetProcAddress_p("eglGetDisplay");
    eglInitialize_p = (eglInitialize_t*) eglGetProcAddress_p("eglInitialize");
    eglChooseConfig_p = (eglChooseConfig_t*) eglGetProcAddress_p("eglChooseConfig");
    eglGetConfigAttrib_p = (eglGetConfigAttrib_t*) eglGetProcAddress_p("eglGetConfigAttrib");
    eglBindAPI_p = (eglBindAPI_t*) eglGetProcAddress_p("eglBindAPI");
    eglCreatePbufferSurface_p = (eglCreatePbufferSurface_t*) eglGetProcAddress_p("eglCreatePbufferSurface");
    eglCreateContext_p = (eglCreateContext_t*) eglGetProcAddress_p("eglCreateContext");
    eglMakeCurrent_p = (eglMakeCurrent_t*) eglGetProcAddress_p("eglMakeCurrent");
    eglGetError_p = (eglGetError_t*) eglGetProcAddress_p("eglGetError");
    eglSwapBuffers_p = (eglSwapBuffers_t*) eglGetProcAddress_p("eglSwapBuffers");
    eglSwapInterval_p = (eglSwapInterval_t*) eglGetProcAddress_p("eglSwapInterval");
}

void* pojavGetCurrentContext() {
    return xrEglContext;
}

int xrEglInit() {
    dlsym_egl();

    if (xrEglDisplay == NULL || xrEglDisplay == EGL_NO_DISPLAY) {
        xrEglDisplay = eglGetDisplay_p(EGL_DEFAULT_DISPLAY);
        if (xrEglDisplay == EGL_NO_DISPLAY) {
            printf("EGLBridge: Error eglGetDefaultDisplay() failed: %p\n", eglGetError_p());
            return 0;
        }
    }

    printf("EGLBridge: Initializing\n");
    // printf("EGLBridge: ANativeWindow pointer = %p\n", androidWindow);
    //(*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/Exception"),"Trace exception");
    if (!eglInitialize_p(xrEglDisplay, NULL, NULL)) {
        printf("EGLBridge: Error eglInitialize() failed: %s\n", eglGetError_p());
        return 0;
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
        printf("EGLBridge: Error couldn't get an EGL visual config: %s\n", eglGetError_p());
        return 0;
    }

    assert(xrConfig);
    assert(num_configs > 0);

    if (!eglGetConfigAttrib_p(xrEglDisplay, xrConfig, EGL_NATIVE_VISUAL_ID, &vid)) {
        printf("EGLBridge: Error eglGetConfigAttrib() failed: %s\n", eglGetError_p());
        return 0;
    }

    eglBindAPI_p(EGL_OPENGL_ES_API);

    xrEglSurface = eglCreatePbufferSurface_p(xrEglDisplay, xrConfig,
                                           NULL);
    if (!xrEglSurface) {
        printf("EGLBridge: Error eglCreatePbufferSurface failed: %d\n", eglGetError_p());
        return 0;
    }

    printf("Created pbuffersurface\n");

    printf("XREGLBridge: Initialized!\n");
    printf("XREGLBridge: ThreadID=%d\n", gettid());
    printf("XREGLBridge: XREGLDisplay=%p, XREGLSurface=%p\n",
/* window==0 ? EGL_NO_CONTEXT : */
           xrEglDisplay,
           xrEglSurface
    );

    return 1;
}

int pojavInit() {
    savedWidth = 1;
    savedHeight = 1;
    printf("XREGLBridge: Thread name is %d\n", gettid());

    return xrEglInit();
}

void pojavSetWindowHint(int hint, int value) {
    // Stub
}


int32_t stride;
void pojavSwapBuffers() {
    eglSwapBuffers_p(xrEglDisplay, xrEglSurface);
}

bool locked = false;
void pojavMakeCurrent(void* window) {
    EGLBoolean success = eglMakeCurrent_p(
            xrEglDisplay,
            xrEglSurface,
            xrEglSurface,
            window
    );

    xrEglContext = window;

    if (success == EGL_FALSE) {
        printf("EGLBridge: Error: eglMakeCurrent() failed: %p\n", eglGetError());
    } else {
        printf("EGLBridge: eglMakeCurrent() succeed!\n");
    }
}

JNIEXPORT JNICALL jlong
Java_pojlib_util_JREUtils_getEGLDisplayPtr(JNIEnv *env, jclass clazz) {
    return (jlong) &xrEglDisplay;
}

JNIEXPORT JNICALL jlong
Java_pojlib_util_JREUtils_getEGLContextPtr(JNIEnv *env, jclass clazz) {
    return (jlong) &xrEglContext;
}

JNIEXPORT JNICALL jlong
Java_pojlib_util_JREUtils_getEGLConfigPtr(JNIEnv *env, jclass clazz) {
    return (jlong) &xrConfig;
}

void* pojavCreateContext(void* contextSrc) {
    const EGLint ctx_attribs[] = {
            EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL_NONE
    };
    EGLContext* ctx = eglCreateContext_p(xrEglDisplay, xrConfig, contextSrc, ctx_attribs);

    printf("XREGLBridge: %p\n", ctx);
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
    eglSwapInterval_p(xrEglDisplay, interval);
}
