package com.sci.cclj.asm.transformers;

import com.sci.cclj.asm.ITransformer;
import com.sci.cclj.computer.LuaJITMachine;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static com.sci.cclj.asm.Constants.*;

public class PullEventScanner implements ITransformer {
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
                            LuaJITMachine.registerSpecialEvent((String) ((LdcInsnNode) prev).cst);
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