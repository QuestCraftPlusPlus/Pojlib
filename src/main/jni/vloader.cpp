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
#include <GLES3/gl32.h>

static JavaVM* jvm;
XrInstanceCreateInfoAndroidKHR* OpenComposite_Android_Create_Info;
XrGraphicsBindingOpenGLESAndroidKHR* OpenComposite_Android_GLES_Binding_Info;

std::string (*OpenComposite_Android_Load_Input_File)(const char *path);

static bool hasInitVulkan = false;

static VkInstance instance;
static VkPhysicalDevice pDev;
static VkDevice dev;
static VkQueue queue;
static int queueFamilyIndex;

static VkImage leftImage;
static VkImage rightImage;

static VkDeviceMemory leftMem;
static VkDeviceMemory rightMem;

static AHardwareBuffer* leftBuffer;
static AHardwareBuffer* rightBuffer;

static const char* instExtensions[] = { VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME,
                                        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
                                        VK_KHR_SURFACE_EXTENSION_NAME };
static const char* devExtensions[] = { VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME,
                                       VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME,
                                       VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME,
                                       VK_KHR_SWAPCHAIN_EXTENSION_NAME,
                                       VK_KHR_MAINTENANCE1_EXTENSION_NAME};

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
        printf("Error!");
    }

    XrLoaderInitInfoAndroidKHR loaderInitInfoAndroidKhr = {
            XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR,
            nullptr,
            jvm,
            ctx
    };

    initializeLoader((const XrLoaderInitInfoBaseHeaderKHR *) &loaderInitInfoAndroidKhr);
}

extern "C"
JNIEXPORT void JNICALL
Java_pojlib_util_VLoader_setEGLGlobal(JNIEnv* env, jclass clazz, jlong ctx, jlong display, jlong cfg) {
    OpenComposite_Android_GLES_Binding_Info = new XrGraphicsBindingOpenGLESAndroidKHR {
            XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR,
            nullptr,
            (void*)display,
            (void*)cfg,
            (void*)ctx
    };
}

void checkVkResult(VkResult res, const char* func) {
    if(res != VK_SUCCESS) {
        printf("%s returned %d!\n", func, res);
        abort();
    }
}

void createInstance() {
    VkApplicationInfo appInfo = {
            .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
            .pApplicationName = "QCXR VK Helper",
            .applicationVersion = VK_MAKE_VERSION(4, 1, 0),
            .pEngineName = "No Engine",
            .engineVersion = 1,
            .apiVersion = VK_VERSION_1_1
    };

    VkInstanceCreateInfo vkInstanceInfo = {
              .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
              .flags = 0,
              .pApplicationInfo = &appInfo,
              .enabledLayerCount = 0,
              .enabledExtensionCount = 3,
              .ppEnabledExtensionNames = instExtensions
    };

    checkVkResult(vkCreateInstance(&vkInstanceInfo, nullptr, &instance), "vkCreateInstance");
}

void pickPhysicalDevice() {
    uint32_t deviceCount = 0;
    checkVkResult(vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr), "vkEnumeratePhysicalDevices");
    VkPhysicalDevice devices[deviceCount];
    checkVkResult(vkEnumeratePhysicalDevices(instance, &deviceCount, devices), "vkEnumeratePhysicalDevices");

    pDev = devices[0];

    uint32_t queueFamilyCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(pDev, &queueFamilyCount, nullptr);
    VkQueueFamilyProperties queueFamilies[queueFamilyCount];
    vkGetPhysicalDeviceQueueFamilyProperties(pDev, &queueFamilyCount, queueFamilies);

    int i = 0;
    for (const auto& queueFamily : queueFamilies) {
        if (queueFamily.queueFlags & VK_QUEUE_GRAPHICS_BIT) {
            queueFamilyIndex = i;
            break;
        }

        i++;
    }
}

void createLogicalDevice() {
    VkDeviceQueueCreateInfo queueCreateInfo{};
    queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueCreateInfo.queueFamilyIndex = queueFamilyIndex;
    queueCreateInfo.queueCount = 1;

    float queuePriority = 1.0f;
    queueCreateInfo.pQueuePriorities = &queuePriority;
    VkPhysicalDeviceFeatures deviceFeatures{};

    VkDeviceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    createInfo.pQueueCreateInfos = &queueCreateInfo;
    createInfo.queueCreateInfoCount = 1;
    createInfo.pEnabledFeatures = &deviceFeatures;

    createInfo.enabledExtensionCount = 5;
    createInfo.ppEnabledExtensionNames = devExtensions;

    checkVkResult(vkCreateDevice(pDev, &createInfo, nullptr, &dev), "vkCreateDevice");

    vkGetDeviceQueue(dev, queueFamilyIndex, 0, &queue);
}

uint32_t findMemoryType(uint32_t typeFilter, VkMemoryPropertyFlags properties) {
    VkPhysicalDeviceMemoryProperties memProperties;
    vkGetPhysicalDeviceMemoryProperties(pDev, &memProperties);

    for (uint32_t i = 0; i < memProperties.memoryTypeCount; i++) {
        if (typeFilter & (1 << i)) {
            return i;
        }
    }

    printf("Error! Couldn't find suitable memory type!\n");
    abort();
}

void createVkImage(uint32_t width, uint32_t height, VkImage* img, VkDeviceMemory* memory, AHardwareBuffer** buffer) {
    AHardwareBuffer_Desc desc = {
            .width = width,
            .height = height,
            .layers = 1,
            .format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM,
            .usage = AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER,
            .rfu0 = 0,
            .rfu1 = 0
    };
    AHardwareBuffer_allocate(&desc, buffer);

    VkExternalMemoryImageCreateInfo emai = {
            .sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
            .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID
    };

    VkImageCreateInfo imgInfo = {
            .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
            .pNext = &emai,
            .imageType = VK_IMAGE_TYPE_2D,
            .format = VK_FORMAT_R8G8B8A8_SRGB,
            .extent = {
                    .width = width,
                    .height = height,
                    .depth = 1
            },
            .mipLevels = 1,
            .arrayLayers = 1,
            .samples = VK_SAMPLE_COUNT_1_BIT,
            .tiling = VK_IMAGE_TILING_LINEAR,
            .usage = VK_IMAGE_USAGE_SAMPLED_BIT,
            .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
            .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
    };

    checkVkResult(vkCreateImage(dev, &imgInfo, nullptr, img), "vkCreateImage");

    VkMemoryRequirements memoryRequirements;
    vkGetImageMemoryRequirements(dev, *img, &memoryRequirements);

    VkAndroidHardwareBufferPropertiesANDROID hardwareBufferPropertiesAndroid = {
            .sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID,
    };
    checkVkResult(vkGetAndroidHardwareBufferPropertiesANDROID(dev, *buffer, &hardwareBufferPropertiesAndroid), "vkGetAndroidHardwareBufferPropertiesANDROID");

    VkImportAndroidHardwareBufferInfoANDROID importAndroidHardwareBufferInfoAndroid =  {
            .sType = VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID,
            .buffer = *buffer
    };

    VkMemoryDedicatedAllocateInfo dedAllocInfo = {
            .sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,
            .pNext = &importAndroidHardwareBufferInfoAndroid,
            .image = *img
    };

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.pNext = &dedAllocInfo;
    allocInfo.allocationSize = hardwareBufferPropertiesAndroid.allocationSize;
    allocInfo.memoryTypeIndex = findMemoryType(hardwareBufferPropertiesAndroid.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

    checkVkResult(vkAllocateMemory(dev, &allocInfo, nullptr, memory), "vkAllocateMemory");
    vkBindImageMemory(dev, *img, *memory, 0);
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_vivecraft_utils_VLoader_getDMABuf(JNIEnv* env, jclass clazz, jboolean isLeft) {
    const native_handle_t* handle = AHardwareBuffer_getNativeHandle(isLeft ? leftBuffer : rightBuffer);
    int dma_buf = (handle && handle->numFds) ? handle->data[0] : -1;
    return dma_buf;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_utils_VLoader_getInstance(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(instance);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_utils_VLoader_getDevice(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(dev);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_utils_VLoader_getPhysicalDevice(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(pDev);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_utils_VLoader_getQueue(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(queue);
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_vivecraft_utils_VLoader_getQueueIndex(JNIEnv* env, jclass clazz) {
    return queueFamilyIndex;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_utils_VLoader_createVKImage(JNIEnv* env, jclass clazz, jint width, jint height, jboolean isLeft) {
    if(!hasInitVulkan) {
        hasInitVulkan = true;
        createInstance();
        pickPhysicalDevice();
        createLogicalDevice();
    }

    createVkImage(width, height, isLeft ? &leftImage : &rightImage, isLeft ? &leftMem : &rightMem, isLeft ? &leftBuffer : &rightBuffer);
    return reinterpret_cast<jlong>((uint64_t) isLeft ? leftImage : rightImage);
}
