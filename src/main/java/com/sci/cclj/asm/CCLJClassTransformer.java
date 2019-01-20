package com.sci.cclj.asm;

import com.sci.cclj.asm.transformers.ComputerThreadTransformer;
import com.sci.cclj.asm.transformers.ComputerTransformer;
import com.sci.cclj.asm.transformers.PullEventScanner;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;

import static com.sci.cclj.asm.Constants.LUACONTEXT_CLASS;

// @TODO: Modify OSAPI queueEvent to prepend a sentinel object to arguments in case event filter is a special event?

public final class CCLJClassTransformer implements IClassTransformer {
    private final Set<String> exclusions;
    private final Map<String, ITransformer> transformers;
    private final List<ITransformer> transformersForAll;

    public CCLJClassTransformer() {
        this.exclusions = new HashSet<>();
        this.transformers = new HashMap<>();
        this.transformersForAll = new ArrayList<>();

        this.exclusions.add(LUACONTEXT_CLASS);

        this.addTransformer(new PullEventScanner());
        this.addTransformer(new ComputerTransformer());
        this.addTransformer(new ComputerThreadTransformer());
    }

    private void addTransformer(final ITransformer transformer) {
        final String clazz = transformer.clazz();

        if(clazz == null) {
            this.transformersForAll.add(transformer);
            return;
        }

        if(this.transformers.containsKey(clazz))
            throw new IllegalArgumentException("Transformer for " + clazz + " already registered!");

        this.transformers.put(clazz, transformer);
    }

    @Override
    public byte[] transform(final String name, final String transformedName, final byte[] basicClass) {
        if(basicClass == null) return null;

        try {
            if(this.exclusions.contains(name)) {
                return basicClass;
            }

            final ClassNode cn = new ClassNode();
            final ClassReader cr = new ClassReader(basicClass);
            cr.accept(cn, 0);

            boolean transformed = this.transformersForAll.stream()
                    .map(t -> t.transform(cn))
                    .reduce(false, (a, b) -> a || b);

            if(this.transformers.containsKey(name)) {
                final ITransformer transformer = this.transformers.get(name);
                if(transformer.transform(cn)) {
                    transformed = true;
                }
            }

            if(transformed) {
                final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                cn.accept(cw);
                return cw.toByteArray();
            } else {
                return basicClass;
            }
        } catch(final Throwable t) {
            t.printStackTrace();
            return basicClass;
        }
    }
}