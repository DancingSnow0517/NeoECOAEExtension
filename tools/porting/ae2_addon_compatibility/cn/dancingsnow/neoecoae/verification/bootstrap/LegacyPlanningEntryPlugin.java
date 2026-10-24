package cn.dancingsnow.neoecoae.verification.bootstrap;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Reproduces the former native-entry-only route without adding production test flags. */
public final class LegacyPlanningEntryPlugin extends UnsafePlanningEntryPlugin {
    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        var nativeEntries = new ClassNode();
        nativeEntries.methods = node.methods.stream().filter(method -> method.name.equals("planJob")).toList();
        super.postApply(target, nativeEntries, mixin, info);
    }
}
