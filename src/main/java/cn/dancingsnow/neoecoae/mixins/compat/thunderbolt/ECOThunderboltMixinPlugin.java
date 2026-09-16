package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Probe resources without loading optional interfaces or their game-class dependencies. */
public final class ECOThunderboltMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean modern = present("com/moakiee/thunderbolt/api/crafting/batch/IBatchCraftingProvider.class");
        if (mixinClassName.endsWith(".ECOThunderboltProviderMixin")) return modern;
        return false;
    }
    private boolean present(String resource) {
        return getClass().getClassLoader().getResource(resource) != null;
    }
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
