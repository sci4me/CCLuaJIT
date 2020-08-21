#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include <jni.h>

extern "C" {
#include <lua.h>
#include <lualib.h>
#include <lauxlib.h>
}


//
// Helper Macros
//

#define CCLJ_JNIVERSION JNI_VERSION_1_8
#define CCLJ_JNIEXPORT(rtype, name, ...) JNIEXPORT rtype JNICALL Java_com_sci_cclj_computer_LuaJITMachine_##name(JNIEnv *env, jobject obj, ##__VA_ARGS__)


//
// Variables
//

static JavaVM *jvm;
static bool initialized = false;

static jclass machine_class = 0;
static jmethodID decode_string_id = 0;
static jfieldID machine_id = 0;
static jfieldID aborted_id = 0;

static jclass iluaobject_class = 0;
static jmethodID get_method_names_id = 0;
static jmethodID call_method_id = 0;

static jclass iluaapi_class = 0;
static jmethodID get_names_id = 0;

static jclass object_class = 0;

static jclass number_class = 0;
static jmethodID doublevalue_id = 0;
static jmethodID intvalue_id = 0;

static jclass integer_class = 0;
static jmethodID integer_valueof_id = 0;

static jclass double_class = 0;
static jmethodID double_valueof_id = 0;

static jclass boolean_class = 0;
static jmethodID boolean_valueof_id = 0;
static jmethodID booleanvalue_id = 0;
static jobject boolean_false = 0;

static jclass string_class = 0;

static jclass bytearray_class = 0;

static jclass objectarray_class = 0;

static jclass map_class = 0;
static jmethodID map_containskey_id = 0;
static jmethodID map_get_id = 0;
static jmethodID map_put_id = 0;
static jmethodID map_entryset_id = 0;

static jclass map_entry_class = 0;
static jmethodID map_entry_getkey_id = 0;
static jmethodID map_entry_getvalue_id = 0;

static jclass hashmap_class = 0;
static jmethodID hashmap_init_id = 0;

static jclass set_class = 0;
static jmethodID set_iterator_id = 0;

static jclass iterator_class = 0;
static jmethodID iterator_hasnext_id = 0;
static jmethodID iterator_next_id = 0;

static jclass identityhashmap_class = 0;
static jmethodID identityhashmap_init_id = 0;

static jclass throwable_class = 0;
static jmethodID exception_getmessage_id = 0;

static jclass interruptedexception_class = 0;


//
// Includes
//

#include "java_fn.cpp"
#include "conversions.cpp"
#include "LuaJITMachine.cpp"


//
// Main Code
//

static jclass get_class_global_ref(JNIEnv *env, const char *name) {
    jclass clazz = env->FindClass(name);
    if(!clazz) return 0;
    return (jclass) env->NewGlobalRef((jobject) clazz);
}

template<typename T>
static T get_pointer(JNIEnv *env, jobject obj, jfieldID f) {
    return (T) env->GetLongField(obj, f);
}

template<typename T>
static bool set_pointer(JNIEnv *env, jobject obj, jfieldID f, T *p) {
    env->SetLongField(obj, f, (jlong) p);
    return true;
}


//
// JNI Code
//

extern "C" {
    JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
        jvm = vm;

        JNIEnv *env;
        if (vm->GetEnv((void **) &env, CCLJ_JNIVERSION) != JNI_OK) {
            return CCLJ_JNIVERSION;
        }

        if(!(machine_class = get_class_global_ref(env, "com/sci/cclj/computer/LuaJITMachine")) ||
            !(decode_string_id = env->GetStaticMethodID(machine_class, "decodeString", "([B)Ljava/lang/String;")) ||
            !(machine_id = env->GetFieldID(machine_class, "machine", "J")) ||
            !(aborted_id = env->GetFieldID(machine_class, "aborted", "Z"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(iluaapi_class = get_class_global_ref(env, "dan200/computercraft/api/lua/ILuaAPI")) ||
            !(get_names_id = env->GetMethodID(iluaapi_class, "getNames", "()[Ljava/lang/String;"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(iluaobject_class = get_class_global_ref(env, "dan200/computercraft/api/lua/ILuaObject")) ||
            !(get_method_names_id = env->GetMethodID(iluaobject_class, "getMethodNames", "()[Ljava/lang/String;")) ||
            !(call_method_id = env->GetMethodID(iluaobject_class, "callMethod", "(Ldan200/computercraft/api/lua/ILuaContext;I[Ljava/lang/Object;)[Ljava/lang/Object;"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(object_class = get_class_global_ref(env, "java/lang/Object"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(number_class = get_class_global_ref(env, "java/lang/Number")) ||
            !(doublevalue_id = env->GetMethodID(number_class, "doubleValue", "()D")) ||
            !(intvalue_id = env->GetMethodID(number_class, "intValue", "()I"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(integer_class = get_class_global_ref(env, "java/lang/Integer")) ||
            !(integer_valueof_id = env->GetStaticMethodID(integer_class, "valueOf", "(I)Ljava/lang/Integer;"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(double_class = get_class_global_ref(env, "java/lang/Double")) ||
            !(double_valueof_id = env->GetStaticMethodID(double_class, "valueOf", "(D)Ljava/lang/Double;"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(boolean_class = get_class_global_ref(env, "java/lang/Boolean")) ||
            !(boolean_valueof_id = env->GetStaticMethodID(boolean_class, "valueOf", "(Z)Ljava/lang/Boolean;")) ||
            !(booleanvalue_id = env->GetMethodID(boolean_class, "booleanValue", "()Z")) ||
            !(boolean_false = env->NewGlobalRef(env->GetStaticObjectField(boolean_class, env->GetStaticFieldID(boolean_class, "FALSE", "Ljava/lang/Boolean;"))))) {
            return CCLJ_JNIVERSION;
        }

        if(!(string_class = get_class_global_ref(env, "java/lang/String"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(bytearray_class = get_class_global_ref(env, "[B"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(objectarray_class = get_class_global_ref(env, "[Ljava/lang/Object;"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(map_class = get_class_global_ref(env, "java/util/Map")) ||
            !(map_containskey_id = env->GetMethodID(map_class, "containsKey", "(Ljava/lang/Object;)Z")) ||
            !(map_get_id = env->GetMethodID(map_class, "get", "(Ljava/lang/Object;)Ljava/lang/Object;")) ||
            !(map_put_id = env->GetMethodID(map_class, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) ||
            !(map_entryset_id = env->GetMethodID(map_class, "entrySet", "()Ljava/util/Set;"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(map_entry_class = get_class_global_ref(env, "java/util/Map$Entry")) ||
            !(map_entry_getkey_id = env->GetMethodID(map_entry_class, "getKey", "()Ljava/lang/Object;")) ||
            !(map_entry_getvalue_id = env->GetMethodID(map_entry_class, "getValue", "()Ljava/lang/Object;"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(hashmap_class = get_class_global_ref(env, "java/util/HashMap")) ||
            !(hashmap_init_id = env->GetMethodID(hashmap_class, "<init>", "()V"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(set_class = get_class_global_ref(env, "java/util/Set")) ||
            !(set_iterator_id = env->GetMethodID(set_class, "iterator", "()Ljava/util/Iterator;"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(iterator_class = get_class_global_ref(env, "java/util/Iterator")) ||
            !(iterator_hasnext_id = env->GetMethodID(iterator_class, "hasNext", "()Z")) ||
            !(iterator_next_id = env->GetMethodID(iterator_class, "next", "()Ljava/lang/Object;"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(identityhashmap_class = get_class_global_ref(env, "java/util/IdentityHashMap")) ||
            !(identityhashmap_init_id = env->GetMethodID(identityhashmap_class, "<init>", "()V"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(throwable_class = get_class_global_ref(env, "java/lang/Throwable")) ||
            !(exception_getmessage_id = env->GetMethodID(throwable_class, "getMessage", "()Ljava/lang/String;"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(throwable_class = get_class_global_ref(env, "java/lang/Throwable"))) {
            return CCLJ_JNIVERSION;
        }

        if(!(interruptedexception_class = get_class_global_ref(env, "java/lang/InterruptedException"))) {
            return CCLJ_JNIVERSION;
        }

        initialized = true;

        return CCLJ_JNIVERSION;
    }

    JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
        JNIEnv *env;
        if (vm->GetEnv((void **) &env, CCLJ_JNIVERSION) != JNI_OK) {
            return;
        }

        if(machine_class)                   env->DeleteGlobalRef(machine_class);
        if(iluaapi_class)                   env->DeleteGlobalRef(iluaapi_class);
        if(iluaobject_class)                env->DeleteGlobalRef(iluaobject_class);
        if(object_class)                    env->DeleteGlobalRef(object_class);
        if(number_class)                    env->DeleteGlobalRef(number_class);
        if(integer_class)                   env->DeleteGlobalRef(integer_class);
        if(double_class)                    env->DeleteGlobalRef(double_class);
        if(boolean_class)                   env->DeleteGlobalRef(boolean_class);
        if(boolean_false)                   env->DeleteGlobalRef(boolean_false);
        if(string_class)                    env->DeleteGlobalRef(string_class);
        if(bytearray_class)                 env->DeleteGlobalRef(bytearray_class);
        if(map_class)                       env->DeleteGlobalRef(map_class);
        if(hashmap_class)                   env->DeleteGlobalRef(hashmap_class);
        if(set_class)                       env->DeleteGlobalRef(set_class);
        if(iterator_class)                  env->DeleteGlobalRef(iterator_class);
        if(identityhashmap_class)           env->DeleteGlobalRef(identityhashmap_class);
        if(throwable_class)                 env->DeleteGlobalRef(throwable_class);
        if(interruptedexception_class)      env->DeleteGlobalRef(throwable_class);
        // TODO: double-check these
    }

    CCLJ_JNIEXPORT(jboolean, initMachine, jstring cclj_version, jstring cc_version, jstring mc_version, jstring host, jstring default_settings, jlong random_seed) {
        auto M = (LuaJITMachine*) calloc(1, sizeof(LuaJITMachine));

        if(!M) return false;

        if(!M->init(env, obj, cclj_version, cc_version, mc_version, host, default_settings, random_seed)) {
            free(M);
            return false;
        }

        assert(set_pointer(env, obj, machine_id, M));

        return true;
    }

    CCLJ_JNIEXPORT(void, deinitMachine) {
        auto M = get_pointer<LuaJITMachine*>(env, obj, machine_id);
        assert(M);

        M->deinit(env);
        free(M);

        set_pointer<LuaJITMachine*>(env, obj, machine_id, 0);
    }

    CCLJ_JNIEXPORT(jboolean, registerAPI, jobject api) {
        auto M = get_pointer<LuaJITMachine*>(env, obj, machine_id);
        assert(M);
        return M->register_api(env, api);
    }

    CCLJ_JNIEXPORT(jboolean, loadBios, jstring bios) {
        auto M = get_pointer<LuaJITMachine*>(env, obj, machine_id);
        assert(M);
        return M->load_bios(env, bios);
    }

    CCLJ_JNIEXPORT(jobjectArray, handleEvent, jobjectArray args) {
        auto M = get_pointer<LuaJITMachine*>(env, obj, machine_id);
        assert(M);
        return M->handle_event(env, args);
    }

    CCLJ_JNIEXPORT(void, abortMachine) {
        auto M = get_pointer<LuaJITMachine*>(env, obj, machine_id);
        assert(M);
        M->abort();
    }
}