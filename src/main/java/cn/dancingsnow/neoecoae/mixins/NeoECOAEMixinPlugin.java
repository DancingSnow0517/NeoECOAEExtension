package cn.dancingsnow.neoecoae.mixins;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class NeoECOAEMixinPlugin implements IMixinConfigPlugin {

    private static final String AE2_TWEAKS = "ae2tweaks";
    private static final String STANDARD_IO_PORT_MIXIN =
            "cn.dancingsnow.neoecoae.mixins.IOPortBlockEntityMixin";
    private static final String AE2_TWEAKS_IO_PORT_MIXIN =
            "cn.dancingsnow.neoecoae.mixins.AE2TweaksIOPortBlockEntityCompatMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean ae2TweaksLoaded = LoadingModList.get().getMods().stream()
                .anyMatch(mod -> AE2_TWEAKS.equals(mod.getModId()));

        if (STANDARD_IO_PORT_MIXIN.equals(mixinClassName)) {
            return !ae2TweaksLoaded;
        }
        if (AE2_TWEAKS_IO_PORT_MIXIN.equals(mixinClassName)) {
            return ae2TweaksLoaded;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
