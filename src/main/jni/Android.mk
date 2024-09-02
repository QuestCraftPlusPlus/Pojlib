LOCAL_PATH := $(call my-dir)
HERE_PATH := $(LOCAL_PATH)

# include $(HERE_PATH)/crash_dump/libbase/Android.mk
# include $(HERE_PATH)/crash_dump/libbacktrace/Android.mk
# include $(HERE_PATH)/crash_dump/debuggerd/Android.mk

LOCAL_PATH := $(HERE_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE := regal
LOCAL_SRC_FILES := GL/libRegal.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := spirv-cross
LOCAL_SRC_FILES := tinywrapper/SPIRVCross/libspirv-cross-c-shared.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := shaderc
LOCAL_SRC_FILES := tinywrapper/shaderc/libshaderc_shared.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := tinywrapper
LOCAL_SHARED_LIBRARIES := regal spirv-cross shaderc
LOCAL_LDLIBS := -lGLESv3
LOCAL_SRC_FILES := tinywrapper/main.c tinywrapper/string_utils.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/tinywrapper
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := openxr_loader
LOCAL_SRC_FILES := libopenxr_loader.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := occore
LOCAL_SRC_FILES := ./OpenOVR/libOCCore.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_SHARED_LIBRARIES := openxr_loader
LOCAL_MODULE := drvopenxr
LOCAL_SRC_FILES := ./OpenOVR/libDrvOpenXR.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := ocovr
LOCAL_STATIC_LIBRARIES := drvopenxr occore drvopenxr
LOCAL_SRC_FILES := ./OpenOVR/OCOVR.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_LDLIBS := -llog -landroid -lGLESv3 -lvulkan -lEGL
LOCAL_CFLAGS := -DXR_USE_PLATFORM_ANDROID -DXR_USE_GRAPHICS_API_OPENGL_ES
LOCAL_SHARED_LIBRARIES := openxr_loader
LOCAL_WHOLE_STATIC_LIBRARIES := ocovr
LOCAL_MODULE := openvr_api
        LOCAL_SRC_FILES := \
                    vloader.cpp
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
# Link GLESv2 for test
LOCAL_LDLIBS := -ldl -llog -landroid -lGLESv3 -lEGL
# -lGLESv2
LOCAL_MODULE := pojavexec
# LOCAL_CFLAGS += -DDEBUG
LOCAL_SHARED_LIBRARIES := openvr_api regal
# -DGLES_TEST
LOCAL_SRC_FILES := \
    egl_bridge.c \
    utils.c \
    input_bridge_v3.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := istdio
LOCAL_SRC_FILES := \
    stdio_is.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := jrelauncher
LOCAL_SHARED_LIBRARIES := pojavexec
LOCAL_LDLIBS := -llog -landroid
LOCAL_SRC_FILES := \
    jre_launcher.c
include $(BUILD_SHARED_LIBRARY)

# Helper to get current thread
# include $(CLEAR_VARS)
# LOCAL_MODULE := thread64helper
# LOCAL_SRC_FILES := thread_helper.cpp
# include $(BUILD_SHARED_LIBRARY)

