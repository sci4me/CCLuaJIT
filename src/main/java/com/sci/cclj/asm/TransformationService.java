package com.sci.cclj.asm;

import com.google.common.collect.Lists;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

public final class TransformationService implements ITransformationService {
    @Nonnull
    @Override
    public String name() {
        return "CCLuaJIT_TransformationService";
    }

    @Override
    public void initialize(final IEnvironment env) {
    }

    @Override
    public void beginScanning(final IEnvironment env) {
    }

    @Override
    public void onLoad(final IEnvironment env, final Set<String> otherServices) {
    }

    @SuppressWarnings("rawtypes")
    @Nonnull
    @Override
    public List<ITransformer> transformers() {
        return Lists.newArrayList(new ComputerExecutorTransformer());
    }
}