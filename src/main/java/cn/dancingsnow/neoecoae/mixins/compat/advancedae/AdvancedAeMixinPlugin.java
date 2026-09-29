package cn.dancingsnow.neoecoae.mixins.compat.advancedae;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Check resources before any mixin can resolve optional AdvancedAE types. */
public final class AdvancedAeMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        ClassLoader loader = getClass().getClassLoader();
        return loader.getResource("net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.class") != null
                && loader.getResource("net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.class") != null
                && loader.getResource("net/pedroksl/advanced_ae/common/cluster/AdvCraftingCPU.class") != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
