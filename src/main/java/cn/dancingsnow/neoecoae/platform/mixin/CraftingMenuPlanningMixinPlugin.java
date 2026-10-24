package cn.dancingsnow.neoecoae.platform.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;

/** Registers menu routing as a normal injector so deferred operation wrappers share its target nodes. */
public final class CraftingMenuPlanningMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {
        InjectionInfo.register(CraftingPlanningInjectionInfo.class);
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> ownTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
