package com.sci.cclj.asm;

import org.objectweb.asm.tree.ClassNode;

public interface ITransformer {
    String clazz();

    boolean transform(final ClassNode cn);
}