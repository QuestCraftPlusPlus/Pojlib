//#import <Foundation/Foundation.h>
#include <stdio.h>
#include <dlfcn.h>
#include <string.h>
#include <malloc.h>

#include "GL/gl.h"
#include "SPIRVCross/include/spirv_cross_c.h"
#include "shaderc/include/shaderc.h"
#include "string_utils.h"

#define LOOKUP_FUNC(func) \
    if (!gles_##func) { \
        gles_##func = dlsym(RTLD_NEXT, #func); \
    } if (!gles_##func) { \
        gles_##func = dlsym(RTLD_DEFAULT, #func); \
    }

int proxy_width, proxy_height, proxy_intformat, maxTextureSize;

void(*gles_glGetTexLevelParameteriv)(GLenum target, GLint level, GLenum pname, GLint *params);
void(*gles_glShaderSource)(GLuint shader, GLsizei count, const GLchar * const *string, const GLint *length);
GLuint (*gles_glCreateShader) (GLenum shaderType);
void(*gles_glTexImage2D)(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const GLvoid *data);
void(*gles_glDrawElementsBaseVertex)(GLenum mode,
                                  GLsizei count,
                                  GLenum type,
                                  void *indices,
                                  GLint basevertex);
void (*gles_glGetBufferParameteriv) (GLenum target, GLenum pname, GLint *params);
void * (*gles_glMapBufferRange) (GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access);
const GLubyte * (*gles_glGetString) (GLenum name);


void *glMapBuffer(GLenum target, GLenum access) {
    // Use: GL_EXT_map_buffer_range
    LOOKUP_FUNC(glGetBufferParameteriv);
    LOOKUP_FUNC(glMapBufferRange);

    GLenum access_range;
    GLint length;

    switch (target) {
        // GL 4.2
        case GL_ATOMIC_COUNTER_BUFFER:

        // GL 4.3
        case GL_DISPATCH_INDIRECT_BUFFER:
        case GL_SHADER_STORAGE_BUFFER	:

        // GL 4.4
        case GL_QUERY_BUFFER:
            printf("ERROR: glMapBuffer unsupported target=0x%x", target);
            break; // not supported for now

	     case GL_DRAW_INDIRECT_BUFFER:
        case GL_TEXTURE_BUFFER:
            printf("ERROR: glMapBuffer unimplemented target=0x%x", target);
            break;
    }

    switch (access) {
        case GL_READ_ONLY:
            access_range = GL_MAP_READ_BIT;
            break;

        case GL_WRITE_ONLY:
            access_range = GL_MAP_WRITE_BIT;
            break;

        case GL_READ_WRITE:
            access_range = GL_MAP_READ_BIT | GL_MAP_WRITE_BIT;
            break;
    }

    gles_glGetBufferParameteriv(target, GL_BUFFER_SIZE, &length);
    return gles_glMapBufferRange(target, 0, length, access_range);
}

static GLenum currShaderType = GL_VERTEX_SHADER;

GLuint glCreateShader(GLenum shaderType) {
    LOOKUP_FUNC(glCreateShader);

    currShaderType = shaderType;

    return gles_glCreateShader(shaderType);
}

static spvc_context context = NULL;
static shaderc_compiler_t compiler = NULL;

void error_callback(void* context, const char* str) {
    printf("SPVC Error! \n%s\n", str);
}

void glShaderSource(GLuint shader, GLsizei count, const GLchar * const *string, const GLint *length) {
    LOOKUP_FUNC(glShaderSource)
    if(context == NULL) {
        spvc_context_create(&context);
        if(context == NULL) {
            printf("SPVC Context could not be created!\n");
        }
    }
    if(compiler == NULL) {
        compiler = shaderc_compiler_initialize();
        if(compiler == NULL) {
            printf("Compiler could not be created!\n");
        }
    }

    // printf("Input GLSL:\n%s", *string);

    shaderc_compile_options_t opts = shaderc_compile_options_initialize();
    shaderc_compile_options_set_forced_version_profile(opts, 450, shaderc_profile_core);
    shaderc_compile_options_set_auto_map_locations(opts, true);
    shaderc_compile_options_set_auto_bind_uniforms(opts, true);
    shaderc_compile_options_set_target_env(opts, shaderc_target_env_opengl, shaderc_env_version_opengl_4_5);

    shaderc_compilation_result_t outSPIRVRes = shaderc_compile_into_spv(compiler, *string,
                                                                        strlen(*string),
                                                                        currShaderType == GL_VERTEX_SHADER ?
                                                                        shaderc_glsl_vertex_shader : shaderc_glsl_fragment_shader,
                                                                        "qcxr_shader", "main", opts);
    if(shaderc_result_get_compilation_status(outSPIRVRes) != shaderc_compilation_status_success) {
        printf("GLSL to SPIRV comp failed!\n%s\n", shaderc_result_get_error_message(outSPIRVRes));
    }

    spvc_parsed_ir ir = NULL;
    spvc_context_set_error_callback(context, &error_callback, NULL);
    spvc_context_parse_spirv(context, (const SpvId *) shaderc_result_get_bytes(outSPIRVRes),
                             shaderc_result_get_length(outSPIRVRes) / sizeof(SpvId), &ir);

    shaderc_result_release(outSPIRVRes);

    spvc_compiler compiler_glsl = NULL;
    spvc_context_create_compiler(context, SPVC_BACKEND_GLSL, ir, SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, &compiler_glsl);

    spvc_compiler_options options = NULL;
    spvc_compiler_create_compiler_options(compiler_glsl, &options);
    spvc_compiler_options_set_uint(options, SPVC_COMPILER_OPTION_GLSL_VERSION, 300);
    spvc_compiler_options_set_bool(options, SPVC_COMPILER_OPTION_GLSL_ENABLE_420PACK_EXTENSION, SPVC_FALSE);
    spvc_compiler_options_set_bool(options, SPVC_COMPILER_OPTION_GLSL_ES, SPVC_TRUE);
    spvc_compiler_install_compiler_options(compiler_glsl, options);
    const char *result = NULL;
    spvc_compiler_compile(compiler_glsl, &result);

    const char* converted = result;

    converted = ReplaceWord(converted, "#version 300 es", "#version 320 es");
    // printf("Output GLSL ES:\n%s", converted);

    gles_glShaderSource(shader, 1, &converted, NULL);

    spvc_context_release_allocations(context);
}

int isProxyTexture(GLenum target) {
    switch (target) {
        case GL_PROXY_TEXTURE_1D:
        case GL_PROXY_TEXTURE_2D:
        case GL_PROXY_TEXTURE_3D:
        case GL_PROXY_TEXTURE_RECTANGLE_ARB:
            return 1;
    }
    return 0;
}

static int inline nlevel(int size, int level) {
    if(size) {
        size>>=level;
        if(!size) size=1;
    }
    return size;
}

void glGetTexLevelParameteriv(GLenum target, GLint level, GLenum pname, GLint *params) {
    LOOKUP_FUNC(glGetTexLevelParameteriv)
    // NSLog("glGetTexLevelParameteriv(%x, %d, %x, %p)", target, level, pname, params);
    if (isProxyTexture(target)) {
        switch (pname) {
            case GL_TEXTURE_WIDTH:
                (*params) = nlevel(proxy_width,level);
                break;
            case GL_TEXTURE_HEIGHT:
                (*params) = nlevel(proxy_height,level);
                break;
            case GL_TEXTURE_INTERNAL_FORMAT:
                (*params) = proxy_intformat;
                break;
        }
    } else {
        gles_glGetTexLevelParameteriv(target, level, pname, params);
    }
}

void glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const GLvoid *data) {
    LOOKUP_FUNC(glTexImage2D)
    if (isProxyTexture(target)) {
        if (!maxTextureSize) {
            glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxTextureSize);
            // maxTextureSize = 16384;
            // NSLog(@"Maximum texture size: %d", maxTextureSize);
        }
        proxy_width = ((width<<level)>maxTextureSize)?0:width;
        proxy_height = ((height<<level)>maxTextureSize)?0:height;
        proxy_intformat = internalformat;
        // swizzle_internalformat((GLenum *) &internalformat, format, type);
    } else {
        gles_glTexImage2D(target, level, internalformat, width, height, border, format, type, data);
    }
}

// Sodium
GLAPI void GLAPIENTRY glMultiDrawElementsBaseVertex(	GLenum mode,
                                       const GLsizei *count,
                                       GLenum type,
                                       const void * const *indices,
                                       GLsizei drawcount,
                                       const GLint *basevertex) {
    LOOKUP_FUNC(glDrawElementsBaseVertex);
    for (int i = 0; i < drawcount; i++) {
        if (count[i] > 0)
            gles_glDrawElementsBaseVertex(mode,
                                     count[i],
                                     type,
                                     indices[i],
                                     basevertex[i]);
    }
}

const GLubyte * glGetString(GLenum name) {
    LOOKUP_FUNC(glGetString);

    switch (name) {
        case GL_VERSION:
            return "4.6";
        case GL_SHADING_LANGUAGE_VERSION:
            return "4.5";
        default:
            return gles_glGetString(name);
    }
}