package com.sci.cclj.computer.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;

final class ReferenceBag<T, R extends Reference<T>> {
    private final BiFunction<T, ReferenceQueue, R> supplier;
    private final Set<Reference<T>> references;
    private final ReferenceQueue<R> referenceQueue;

    ReferenceBag(final BiFunction<T, ReferenceQueue, R> supplier) {
        this.supplier = supplier;
        this.references = new HashSet<>();
        this.referenceQueue = new ReferenceQueue<>();
    }

    void add(final T obj) {
        this.references.add(this.supplier.apply(obj, this.referenceQueue));
    }

    T poll() {
        final Iterator<Reference<T>> it = this.references.iterator();
        while(it.hasNext()) {
            final Reference<T> ref = it.next();
            it.remove();

            final T obj = ref.get();
            if(obj != null) {
                return obj;
            }
        }
        return null;
    }
}