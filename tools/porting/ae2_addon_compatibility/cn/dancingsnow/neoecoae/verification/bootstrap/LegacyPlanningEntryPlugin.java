package cn.dancingsnow.neoecoae.verification.bootstrap;

import cn.dancingsnow.neoecoae.platform.mixin.CraftingMenuPlanningMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Reproduces the former native-entry-only route without adding production test flags. */
public final class LegacyPlanningEntryPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> ownTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        var nativeEntries = new ClassNode();
        nativeEntries.methods = node.methods.stream().filter(method -> method.name.equals("planJob")).toList();
        new CraftingMenuPlanningMixinPlugin().postApply(target, nativeEntries, mixin, info);
    }
}
