#include "../GL/gl.h"
#include <EGL/egl.h>
#include <dlfcn.h>

typedef __eglMustCastToProperFunctionPointerType eglGetProcAddress_t (const char *procname);
eglGetProcAddress_t* eglGetProcAddress_p;

void* glXGetProcAddress(const GLubyte* name) {
    if(eglGetProcAddress_p == NULL) {
        void* handle = dlopen("libEGL_mesa.so", RTLD_NOW);
        eglGetProcAddress_p = dlsym(handle, "eglGetProcAddress");
    }
    return eglGetProcAddress_p(name);
}