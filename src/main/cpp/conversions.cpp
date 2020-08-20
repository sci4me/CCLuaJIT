static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject valuesInProgress, jobject machine);
static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject machine);
static jobject table_to_map(JNIEnv *env, lua_State *L, int objectsInProgress);
static jobject table_to_map(JNIEnv *env, lua_State *L);
static void to_lua_value(JNIEnv *env, lua_State *L, jobject value, jobject machine);
static void to_lua_values(JNIEnv *env, lua_State *L, jobjectArray values, jobject machine);
static jobject to_java_value(JNIEnv *env, lua_State *L);
static jobjectArray to_java_values(JNIEnv *env, lua_State *L, int n);


static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject valuesInProgress, jobject machine) {
    if(env->CallBooleanMethod(valuesInProgress, map_containskey_id, map)) {
        jobject bidx = env->CallObjectMethod(valuesInProgress, map_get_id, map);
        jint idx = env->CallStaticIntMethod(integer_class, intvalue_id, bidx);
        lua_pushvalue(L, idx);
        return;
    }

    lua_newtable(L);
    int idx = lua_gettop(L);

    env->CallObjectMethod(valuesInProgress, map_put_id, map, env->CallStaticObjectMethod(integer_class, integer_valueof_id, idx));

    jobject entrySet = env->CallObjectMethod(map, map_entryset_id);
    jobject iterator = env->CallObjectMethod(entrySet, set_iterator_id);

    while(env->CallBooleanMethod(iterator, iterator_hasnext_id)) {
        jobject next = env->CallObjectMethod(iterator, iterator_next_id);
        jobject key = env->CallObjectMethod(next, map_entry_getkey_id);
        jobject value = env->CallObjectMethod(next, map_entry_getvalue_id);

        to_lua_value(env, L, key, machine);
        to_lua_value(env, L, value, machine);
        lua_settable(L, idx);
    }
}

static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject machine) {
    jobject vip = env->NewObject(identityhashmap_class, identityhashmap_init_id);
    map_to_table(env, L, map, vip, machine);
}

static jobject table_to_map(JNIEnv *env, lua_State *L, int objectsInProgress) {
    int t = lua_gettop(L);

    lua_pushvalue(L, t);
    lua_gettable(L, objectsInProgress);
    if(!lua_isnil(L, -1)) {
        jobject oip = (jobject) lua_touserdata(L, -1);
        lua_pop(L, 1);
        return oip;
    } else {
        lua_pop(L, 1);
    }

    jobject map = env->NewObject(hashmap_class, hashmap_init_id);
    lua_pushvalue(L, t);
    lua_pushlightuserdata(L, map);
    lua_settable(L, objectsInProgress);

    lua_pushnil(L);
    while(lua_next(L, t)) {
        jobject value = to_java_value(env, L);
        lua_pushvalue(L, -1);
        jobject key = to_java_value(env, L);
        env->CallObjectMethod(map, map_put_id, key, value);
    }

    lua_pop(L, 1);

    return map;
}

static jobject table_to_map(JNIEnv *env, lua_State *L) {
    lua_newtable(L);
    int oip = lua_gettop(L);
    lua_pushvalue(L, -2);
    jobject result = table_to_map(env, L, oip);
    lua_pop(L, 2);
    return result;
}

static void to_lua_value(JNIEnv *env, lua_State *L, jobject value, jobject machine) {
    if(value == 0) {
        lua_pushnil(L);
    } else if(env->IsInstanceOf(value, number_class)) {
        jdouble n = (jdouble) env->CallDoubleMethod(value, doublevalue_id);
        lua_pushnumber(L, n);
    } else if(env->IsInstanceOf(value, boolean_class)) {
        jboolean b = (jboolean) env->CallBooleanMethod(value, booleanvalue_id);
        lua_pushboolean(L, b);
    } else if(env->IsInstanceOf(value, string_class)) {
        jstring str = (jstring) value;
        const char *cstr = env->GetStringUTFChars(str, JNI_FALSE);
        lua_pushstring(L, cstr);
        env->ReleaseStringUTFChars(str, cstr);
    } else if(env->IsInstanceOf(value, bytearray_class)) {
        jbyteArray a = (jbyteArray) value;
        jsize alen = env->GetArrayLength(a);
        jbyte *ca = env->GetByteArrayElements(a, JNI_FALSE);

        char *rca = (char*) malloc(alen + 1);
        memcpy(rca, ca, alen);
        rca[alen] = 0;
        lua_pushstring(L, rca);
        free(rca);

        env->ReleaseByteArrayElements(a, ca, JNI_ABORT);
    } else if(env->IsInstanceOf(value, map_class)) {
        map_to_table(env, L, value, machine);
    } else if(env->IsInstanceOf(value, iluaobject_class)) {
        wrap_lua_object(env, L, value, machine);
    } else if(env->IsInstanceOf(value, objectarray_class)) {
        jobjectArray a = (jobjectArray) value;
        jsize alen = env->GetArrayLength(a);

        lua_newtable(L);
        int table = lua_gettop(L);

        for(jsize i = 0; i < alen; i++) {
            jobject jobj = env->GetObjectArrayElement(a, i);

            lua_pushnumber(L, i + 1);
            to_lua_value(env, L, jobj, machine);
            lua_settable(L, table);

            env->DeleteLocalRef(jobj);
        }
    } else {
        jclass cls = env->GetObjectClass(value);
        jmethodID mid = env->GetMethodID(cls, "getClass", "()Ljava/lang/Class;");
        jobject clsObj = env->CallObjectMethod(value, mid);
        cls = env->GetObjectClass(clsObj);
        mid = env->GetMethodID(cls, "getSimpleName", "()Ljava/lang/String;");
        jstring strObj = (jstring)env->CallObjectMethod(clsObj, mid);
        char const* s = env->GetStringUTFChars(strObj, JNI_FALSE);

        char buf[1024];
        snprintf(buf, sizeof(buf), "Attempt to convert unrecognized Java value (%s) to a Lua value!", s);

        env->ReleaseStringUTFChars(strObj, s);
        env->DeleteLocalRef(strObj);

        luaL_error(L, buf);
    }
}

static void to_lua_values(JNIEnv *env, lua_State *L, jobjectArray values, jobject machine) {
    jsize len = env->GetArrayLength(values);
    for(jsize i = 0; i < len; i++) {
        jobject elem = env->GetObjectArrayElement(values, i);
        to_lua_value(env, L, elem, machine);
        env->DeleteLocalRef(elem);
    }
}

static jobject to_java_value(JNIEnv *env, lua_State *L) {
    int t = lua_type(L, -1);
    switch(t) {
        case LUA_TNIL:
        case LUA_TNONE:
            lua_pop(L, 1);
            return 0;
        case LUA_TNUMBER: {
            lua_Number n = lua_tonumber(L, -1);
            lua_pop(L, 1);
            return env->CallStaticObjectMethod(double_class, double_valueof_id, n);
        }
        case LUA_TBOOLEAN: {
            int b = lua_toboolean(L, -1);
            lua_pop(L, 1);
            return env->CallStaticObjectMethod(boolean_class, boolean_valueof_id, b);
        }
        case LUA_TSTRING: {
            size_t len;
            const char *cstr = lua_tolstring(L, -1, &len);

            jbyteArray bytes = env->NewByteArray(len);
            env->SetByteArrayRegion(bytes, 0, len, (jbyte*) cstr);

            jobject result = env->CallStaticObjectMethod(machine_class, decode_string_id, bytes);

            lua_pop(L, 1);

            return result;
        }
        case LUA_TTABLE:
            return table_to_map(env, L);
        case LUA_TFUNCTION:
            luaL_error(L, "Attempt to convert a Lua function to a Java value!");
            break;
        case LUA_TTHREAD:
            luaL_error(L, "Attempt to convert a Lua thread to a Java value!");
            break;
        case LUA_TUSERDATA:
            luaL_error(L, "Attempt to convert userdata to a Java value!");
            break;
        case LUA_TLIGHTUSERDATA:
            luaL_error(L, "Attempt to convert lightuserdata to a Java value!");
            break;
        default:
            luaL_error(L, "Attempt to convert unrecognized Lua value to a Java value!");
            break;
    }

    return 0;
}

static jobjectArray to_java_values(JNIEnv *env, lua_State *L, int n) {
    jobjectArray result = env->NewObjectArray(n, object_class, 0);
    for(int i = n; i > 0; i--) {
        env->SetObjectArrayElement(result, i - 1, to_java_value(env, L));
    }
    return result;
}