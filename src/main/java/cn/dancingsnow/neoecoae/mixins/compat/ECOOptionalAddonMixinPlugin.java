package cn.dancingsnow.neoecoae.mixins.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Guards even base-AE2 targets whose injected method bodies reference optional addon types. */
public final class ECOOptionalAddonMixinPlugin implements IMixinConfigPlugin {
    private String anchor;

    @Override public void onLoad(String mixinPackage) {
        anchor = switch (mixinPackage) {
            case "cn.dancingsnow.neoecoae.mixins.compat.appmek" ->
                    "me/ramidzkh/mekae2/ae2/stack/MekanismExternalStorageStrategy.class";
            case "cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells" ->
                    "com/wintercogs/ae2omnicells/common/me/IAEUniversalCell.class";
            case "cn.dancingsnow.neoecoae.mixins.compat.advancedae" ->
                    "net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.class";
            case "cn.dancingsnow.neoecoae.mixins.compat.extendedae" ->
                    "com/glodblock/github/extendedae/common/tileentities/TileExIOPort.class";
            case "cn.dancingsnow.neoecoae.mixins.compat.gregtech" ->
                    "com/gregtechceu/gtceu/integration/ae2/machine/MEPatternBufferPartMachine.class";
            default -> throw new IllegalArgumentException("Unsupported optional mixin package: " + mixinPackage);
        };
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".MekanismExternalStorageStrategyMixin")
            && getClass().getClassLoader().getResource("mekanism/api/chemical/IChemicalHandler.class") == null) return false;
        if (mixinClassName.endsWith(".OmniWasteCellRecognitionMixin")
            && getClass().getClassLoader().getResource("me/ramidzkh/mekae2/ae2/MekanismKey.class") == null) return false;
        return anchor != null && getClass().getClassLoader().getResource(anchor) != null;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
