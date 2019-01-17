package com.sci.cclj.computer.util;

import java.lang.ref.SoftReference;
import java.util.function.Supplier;

public final class ReferencingCache<T> {
    private final Supplier<T> factory;
    private final ReferenceBag<T, SoftReference<T>> cache;

    public ReferencingCache(final Supplier<T> factory) {
        this.factory = factory;
        this.cache = new ReferenceBag<>(SoftReference::new);
    }

    public T borrowObject() {
        final T fromCache = this.cache.poll();
        if(fromCache == null) {
            return this.factory.get();
        } else {
            return fromCache;
        }
    }

    public void returnObject(final T object) {
        this.cache.add(object);
    }
}