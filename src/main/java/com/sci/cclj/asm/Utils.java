package com.sci.cclj.asm;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class Utils {
    public static void dumpClass(final ClassNode cn) {
        final StringWriter writer = new StringWriter();
        final TraceClassVisitor visitor = new TraceClassVisitor(new PrintWriter(writer));
        cn.accept(visitor);
        System.out.println(writer.toString());
    }

    public static void dumpMethod(final MethodNode mn) {
        final Printer printer = new Textifier();
        final TraceMethodVisitor visitor = new TraceMethodVisitor(printer);
        mn.accept(visitor);
        final StringWriter writer = new StringWriter();
        printer.print(new PrintWriter(writer));
        System.out.println(writer.toString());
    }
}