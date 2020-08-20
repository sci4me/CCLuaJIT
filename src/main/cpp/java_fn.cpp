static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject valuesInProgress, jobject machine);
static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject machine);
static jobject table_to_map(JNIEnv *env, lua_State *L, int objectsInProgress);
static jobject table_to_map(JNIEnv *env, lua_State *L);
static void to_lua_value(JNIEnv *env, lua_State *L, jobject value, jobject machine);
static void to_lua_values(JNIEnv *env, lua_State *L, jobjectArray values, jobject machine);
static jobject to_java_value(JNIEnv *env, lua_State *L);
static jobjectArray to_java_values(JNIEnv *env, lua_State *L, int n);


extern "C" {
    static void thread_yield_request_handler_hook(lua_State *L, lua_Debug *ar);
    static int finalize_java_fn(lua_State *L);
}

struct JavaFN {
    jobject machine;
    jobject obj;
    int index;

    void init(JNIEnv *env, lua_State *L, jobject obj, jobject machine, int index) {
        this->obj = env->NewGlobalRef(obj);
        this->machine = env->NewGlobalRef(machine);
        this->index = index;

        if(luaL_newmetatable(L, "JavaFN")) {
            lua_pushstring(L, "__gc");
            lua_pushcfunction(L, finalize_java_fn);
            lua_settable(L, -3);
        }

        lua_setmetatable(L, -2);
    }

    bool finalize(lua_State *L) {
        JNIEnv *env;
        if(jvm->GetEnv((void**) &env, CCLJ_JNIVERSION) != JNI_OK) {
            luaL_error(L, "JavaFN finalizer could not retrieve JNIEnv");
            return false;
        }

        env->DeleteGlobalRef(machine);
        env->DeleteGlobalRef(obj);

        obj = 0;

        return true;
    }

    int invoke(lua_State *L) {
        JNIEnv *env;
        if(jvm->GetEnv((void**) &env, CCLJ_JNIVERSION) != JNI_OK) {
            luaL_error(L, "JavaFN invocation wrapper could not retrieve JNIEnv");
            return 0;
        }

        if(env->GetBooleanField(machine, aborted_id)) {
            luaL_error(L, "aborted");
            return 0;
        }

        jobjectArray arguments = to_java_values(env, L, lua_gettop(L));
        jobjectArray results = (jobjectArray) env->CallObjectMethod(obj, call_method_id, machine, index, arguments);

        if(env->ExceptionCheck()) {
            jthrowable e = env->ExceptionOccurred();

            //if(!env->IsInstanceOf(e, interruptedexception_class)) {
                env->ExceptionClear();

                lua_Debug ar;
                memset(&ar, 0, sizeof(lua_Debug));
                if(lua_getstack(L, 1, &ar) && lua_getinfo(L, "Sl", &ar)) {
                    char buf[512];
                    sprintf(buf, "%s:%d: ", ar.short_src, ar.currentline);
                    lua_pushstring(L, buf);
                } else {
                    lua_pushstring(L, "?:?: ");
                }

                jstring message = (jstring) env->CallObjectMethod(e, exception_getmessage_id);
                if(message) {
                    const char *messagec = env->GetStringUTFChars(message, JNI_FALSE);
                    lua_pushstring(L, messagec);
                    env->ReleaseStringUTFChars(message, messagec);
                } else {
                    lua_pushstring(L, "an unknown error occurred");
                }

                lua_concat(L, 2);

                env->DeleteLocalRef(message);
                env->DeleteLocalRef(e);

                return lua_error(L);
            //}

            //env->DeleteLocalRef(e);
        }

        if(results) {
            to_lua_values(env, L, results, machine);
            return env->GetArrayLength(results);
        } else {
            return 0;
        }
    }
};


extern "C" {
    static int invoke_java_fn(lua_State *L) {
        JavaFN *jfn = (JavaFN*) lua_touserdata(L, lua_upvalueindex(1));
        return jfn->invoke(L);
    }

    static int finalize_java_fn(lua_State *L) {
        JavaFN *jfn = (JavaFN*) luaL_checkudata(L, 1, "JavaFN");
        if(!jfn->obj) luaL_error(L, "Attempt to finalize a finalized JavaFN");
        jfn->finalize(L);
        return 0;
    }
}

static int wrap_lua_object(JNIEnv *env, lua_State *L, jobject obj, jobject machine) {
    lua_newtable(L);
    int table = lua_gettop(L);

    jobjectArray methodNames = (jobjectArray) env->CallObjectMethod(obj, get_method_names_id);
    jsize len = env->GetArrayLength(methodNames);
    for(jsize i = 0; i < len; i++) {
        jstring methodName = (jstring) env->GetObjectArrayElement(methodNames, i);
        const char *methodNamec = env->GetStringUTFChars(methodName, JNI_FALSE);

        lua_pushstring(L, methodNamec);
        auto jfn = (JavaFN*) lua_newuserdata(L, sizeof(JavaFN));
        jfn->init(env, L, obj, machine, i);
        lua_pushcclosure(L, invoke_java_fn, 1);
        lua_settable(L, table);

        env->ReleaseStringUTFChars(methodName, methodNamec);
        env->DeleteLocalRef(methodName);
    }

    return table;
}