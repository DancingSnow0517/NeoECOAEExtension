package cn.dancingsnow.neoecoae.mixins;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Loads GTLCore bridge mixins only when GTLCore is installed. */
public final class GTLCoreMixinPlugin implements IMixinConfigPlugin {
    private static final String GTLCORE_MOD_ID = "gtlcore";
    private boolean loaded;

    @Override
    public void onLoad(String mixinPackage) {
        loaded = net.minecraftforge.fml.ModList.get().isLoaded(GTLCORE_MOD_ID);
    }

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return loaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
