package com.sci.cclj.asm.transformers;

import com.sci.cclj.asm.ITransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashSet;
import java.util.Set;

import static com.sci.cclj.asm.Constants.ILUACONTEXT_DESC;
import static com.sci.cclj.asm.Constants.PULLEVENT_DESC;

public class PullEventScanner implements ITransformer {
    private static final Object specialEventsLock = new Object();
    private static final Set<String> specialEvents = new HashSet<>();

    public static void registerSpecialEvent(final String filter) {
        synchronized(PullEventScanner.specialEventsLock) {
            PullEventScanner.specialEvents.add(filter);
        }
    }

    public static boolean isSpecialEvent(final String filter) {
        synchronized(PullEventScanner.specialEventsLock) {
            return PullEventScanner.specialEvents.contains(filter);
        }
    }

    @Override
    public String clazz() {
        return null;
    }

    @Override
    public boolean transform(final ClassNode cn) {
        for(final MethodNode mn : cn.methods) {
            for(final AbstractInsnNode insn : mn.instructions.toArray()) {
                if(insn instanceof MethodInsnNode) {
                    final MethodInsnNode minsn = (MethodInsnNode) insn;
                    if(minsn.owner.equals(ILUACONTEXT_DESC) &&
                            (minsn.name.equals("pullEvent") || minsn.name.equals("pullEventRaw")) &&
                            minsn.desc.equals(PULLEVENT_DESC)) {
                        final AbstractInsnNode prev = mn.instructions.get(mn.instructions.indexOf(minsn) - 1);
                        if(prev.getOpcode() == Opcodes.LDC) {
                            PullEventScanner.registerSpecialEvent((String) ((LdcInsnNode) prev).cst);
                        } else {
                            throw new RuntimeException("Found call to pullEvent(raw?) but could not determine its event filter!");
                        }
                    }
                }
            }
        }

        return false;
    }
}