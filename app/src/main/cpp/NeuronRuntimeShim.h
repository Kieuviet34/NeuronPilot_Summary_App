#ifndef NEURON_RUNTIME_SHIM_H
#define NEURON_RUNTIME_SHIM_H

#include <dlfcn.h>
#include <stddef.h>
#include <stdint.h>
#include <android/log.h>

#define NEURON_RT_LOG_TAG "NeuronRtShim"
#define NEURON_RUNTIME_LIB "libneuron_runtime.so"

// ============================================================
// Structs & Enums used by Neuron Runtime API V1
// ============================================================
typedef enum {
    BUFFER_ATTR_NORMAL = 0,  // Ordinary CPU memory
} BufferAttribute;

typedef struct {
    int preference;    // 1 = kFastSingleAnswer
    int priority;      // 110 = kPriorityHigh
    int boostValue;    // 100
    int boostDuration; // 0
} QoSOptions;

typedef struct {
    int tunedPreference; // 0 = normal
} EnvOptions;

// ============================================================
// Function pointer typedefs (matching Neuron Runtime V1 symbol names)
// ============================================================
typedef int  (*fn_NeuronRuntime_create)              (const EnvOptions* options, void** runtime);
typedef int  (*fn_NeuronRuntime_loadNetworkFromFile)  (void* runtime, const char* dlaFile);
typedef int  (*fn_NeuronRuntime_getInputNumber)       (void* runtime, size_t* size);
typedef int  (*fn_NeuronRuntime_getInputSize)         (void* runtime, uint64_t handle, size_t* size);
typedef int  (*fn_NeuronRuntime_setInput)             (void* runtime, uint64_t handle, const void* buffer, size_t length, BufferAttribute attr);
typedef int  (*fn_NeuronRuntime_getOutputNumber)      (void* runtime, size_t* size);
typedef int  (*fn_NeuronRuntime_getOutputSize)        (void* runtime, uint64_t handle, size_t* size);
typedef int  (*fn_NeuronRuntime_setOutput)            (void* runtime, uint64_t handle, void* buffer, size_t length, BufferAttribute attr);
typedef int  (*fn_NeuronRuntime_setQoSOption)         (void* runtime, const QoSOptions* qosOption);
typedef int  (*fn_NeuronRuntime_inference)            (void* runtime);
typedef void (*fn_NeuronRuntime_release)              (void* runtime);

// ============================================================
// NeuronRuntimeShim: loads and exposes all API function pointers
// ============================================================
class NeuronRuntimeShim {
public:
    fn_NeuronRuntime_create              NeuronRuntime_create              = nullptr;
    fn_NeuronRuntime_loadNetworkFromFile NeuronRuntime_loadNetworkFromFile = nullptr;
    fn_NeuronRuntime_getInputNumber      NeuronRuntime_getInputNumber      = nullptr;
    fn_NeuronRuntime_getInputSize        NeuronRuntime_getInputSize        = nullptr;
    fn_NeuronRuntime_setInput            NeuronRuntime_setInput            = nullptr;
    fn_NeuronRuntime_getOutputNumber     NeuronRuntime_getOutputNumber     = nullptr;
    fn_NeuronRuntime_getOutputSize       NeuronRuntime_getOutputSize       = nullptr;
    fn_NeuronRuntime_setOutput           NeuronRuntime_setOutput           = nullptr;
    fn_NeuronRuntime_setQoSOption        NeuronRuntime_setQoSOption        = nullptr;
    fn_NeuronRuntime_inference           NeuronRuntime_inference           = nullptr;
    fn_NeuronRuntime_release             NeuronRuntime_release             = nullptr;

    bool loaded = false;

    bool Load() {
        // dlopen: tìm trong thư mục lib của App trước, sau đó mới tìm system
        void* handle = dlopen(NEURON_RUNTIME_LIB, RTLD_LAZY | RTLD_LOCAL);
        if (!handle) {
            __android_log_print(ANDROID_LOG_ERROR, NEURON_RT_LOG_TAG,
                "dlopen(%s) failed: %s", NEURON_RUNTIME_LIB, dlerror());
            return false;
        }

        // Macro: load function pointer từ shared library
        #define LOAD_SYM(member, symbol) \
            member = (fn_##symbol) dlsym(handle, #symbol); \
            if (!(member)) { \
                __android_log_print(ANDROID_LOG_ERROR, NEURON_RT_LOG_TAG, \
                    "dlsym(" #symbol ") failed: %s", dlerror()); \
                return false; \
            } \
            __android_log_print(ANDROID_LOG_DEBUG, NEURON_RT_LOG_TAG, \
                "Loaded: " #symbol);

        LOAD_SYM(NeuronRuntime_create,              NeuronRuntime_create)
        LOAD_SYM(NeuronRuntime_loadNetworkFromFile, NeuronRuntime_loadNetworkFromFile)
        LOAD_SYM(NeuronRuntime_getInputNumber,      NeuronRuntime_getInputNumber)
        LOAD_SYM(NeuronRuntime_getInputSize,        NeuronRuntime_getInputSize)
        LOAD_SYM(NeuronRuntime_setInput,            NeuronRuntime_setInput)
        LOAD_SYM(NeuronRuntime_getOutputNumber,     NeuronRuntime_getOutputNumber)
        LOAD_SYM(NeuronRuntime_getOutputSize,       NeuronRuntime_getOutputSize)
        LOAD_SYM(NeuronRuntime_setOutput,           NeuronRuntime_setOutput)
        LOAD_SYM(NeuronRuntime_setQoSOption,        NeuronRuntime_setQoSOption)
        LOAD_SYM(NeuronRuntime_inference,           NeuronRuntime_inference)
        LOAD_SYM(NeuronRuntime_release,             NeuronRuntime_release)
        #undef LOAD_SYM

        __android_log_print(ANDROID_LOG_DEBUG, NEURON_RT_LOG_TAG,
            "All Neuron Runtime V1 functions loaded successfully!");
        loaded = true;
        return true;
    }
};

// Global singleton (lazy init)
inline NeuronRuntimeShim& GetNeuronRt() {
    static NeuronRuntimeShim shim;
    if (!shim.loaded) shim.Load();
    return shim;
}

#endif // NEURON_RUNTIME_SHIM_H
