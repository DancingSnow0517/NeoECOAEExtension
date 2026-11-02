package cn.dancingsnow.neoecoae.mixins.compat.omnisequence;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Decline older Omni versions before the JVM resolves optional provider interfaces. */
public final class ECOOmniSequenceMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            var api = Class.forName("com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingApi",
                false, getClass().getClassLoader());
            return (int) api.getMethod("apiVersion").invoke(null) == 1;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
