package cn.dancingsnow.neoecoae.mixins;

import cn.dancingsnow.neoecoae.config.NELogConfig;

import cn.dancingsnow.neoecoae.platform.mixin.CraftingPlanningInjectionInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.service.MixinService;

public final class ECOMixinLoadConfig implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE = "cn.dancingsnow.neoecoae.mixins";
    private static final String THUNDERBOLT_MIXINS = "compat.thunderbolt.";
    private static final String THUNDERBOLT_BATCH = "com/moakiee/thunderbolt/api/crafting/batch/";
    private static final Map<String, String> ADDON_ANCHORS = Map.of(
        "compat.advancedae.", "net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.class",
        "compat.ae2omnicells.", "com/wintercogs/ae2omnicells/common/me/IAEUniversalCell.class",
        "compat.appmek.", "me/ramidzkh/mekae2/ae2/stack/MekanismExternalStorageStrategy.class",
        "compat.extendedae.", "com/glodblock/github/extendedae/common/tileentities/TileExIOPort.class",
        "compat.gregtech.", "com/gregtechceu/gtceu/integration/ae2/machine/MEPatternBufferPartMachine.class"
    );
    private static final Set<String> AE2_VERSION_CHECKED = Set.of(
        "ae2.accessor.PrecisePriorityListAccessor",
        "ae2.storage.DelegatingStorageListingMixin",
        "ae2.storage.MEInventoryHandlerListingMixin",
        "ae2.storage.TerminalSnapshotCounterMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
        if (MIXIN_PACKAGE.equals(mixinPackage)) {
            InjectionInfo.register(CraftingPlanningInjectionInfo.class);
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String mixin = mixinClassName.substring(MIXIN_PACKAGE.length() + 1);
        if (AE2_VERSION_CHECKED.contains(mixin) || mixin.startsWith("ae2.key.")) {
            var mods = LoadingModList.get();
            return mods != null && mods.getMods().stream().anyMatch(mod -> mod.getModId().equals("ae2")
                && mod.getVersion().toString().equals("15.4.11"));
        }
        if (mixin.startsWith(THUNDERBOLT_MIXINS)) {
            return switch (mixin.substring(THUNDERBOLT_MIXINS.length())) {
                case "Ae2LtIOPortBlockEntityMixin", "Ae2LtIOPortMenuMixin", "Ae2LtTimeWheelCraftingCpuLogicMixin" ->
                    hasTarget(targetClassName);
                case "ECOThunderboltWorkstationProviderMixin" ->
                    hasClassResource(THUNDERBOLT_BATCH + "IBatchCraftingProvider.class")
                        && hasClassResource(THUNDERBOLT_BATCH + "BatchJobView.class");
                default -> false;
            };
        }
        if (mixin.startsWith("jei.client.") || mixin.startsWith("compat.merequester.")
                || mixin.startsWith("compat.ae2wtlib.")) {
            return hasTarget(targetClassName);
        }
        return switch (mixin) {
            case "ae2.keyreuse.MekanismKeyReuseMixin" -> supportsMekanismKey(targetClassName);
            case "ae2.storage.StorageServiceMixin" ->
                !hasClassResource("org/gtlcore/gtlcore/integration/ae2/InventorySnapshotVersion.class");
            case "ae2.storage.NetworkStoragePerformanceMixin" -> Boolean.getBoolean("neoecoae.storagePerformance");
            case "compat.gregtech.GTLFastInfinityCellTransferMixin" -> hasTarget(targetClassName);
            case "compat.gregtech.GTLInfinityCellInventoryMixin" ->
                hasClassResource("org/gtlcore/gtlcore/integration/ae2/storage/PreciseStorageAmount.class")
                    && hasTarget(targetClassName);
            case "compat.appmek.MekanismExternalStorageStrategyMixin" ->
                hasClassResource("mekanism/api/chemical/IChemicalHandler.class") && hasAddonAnchor(mixin);
            case "compat.ae2omnicells.OmniWasteCellRecognitionMixin" ->
                hasClassResource("me/ramidzkh/mekae2/ae2/MekanismKey.class") && hasAddonAnchor(mixin);
            default -> hasAddonAnchor(mixin);
        };
    }

    private static boolean hasAddonAnchor(String mixin) {
        for (var requirement : ADDON_ANCHORS.entrySet()) {
            if (mixin.startsWith(requirement.getKey())) {
                return hasClassResource(requirement.getValue());
            }
        }
        return true;
    }

    private static boolean hasTarget(String targetClassName) {
        return hasClassResource(targetClassName.replace('.', '/') + ".class");
    }

    private static boolean hasClassResource(String resource) {
        return ECOMixinLoadConfig.class.getClassLoader().getResource(resource) != null;
    }

    private static boolean supportsMekanismKey(String targetClassName) {
        String stackType = "Lmekanism/api/chemical/ChemicalStack;";
        try {
            var node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            boolean field = node.fields.stream().anyMatch(value -> value.name.equals("stack")
                && value.desc.equals(stackType) && (value.access & Opcodes.ACC_STATIC) == 0);
            boolean getter = node.methods.stream().anyMatch(method -> method.name.equals("getStack")
                && method.desc.equals("()" + stackType) && (method.access & Opcodes.ACC_STATIC) == 0);
            boolean hashCode = node.methods.stream().anyMatch(method ->
                method.name.equals("hashCode") && method.desc.equals("()I"));
            return field && getter && hashCode;
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Exception | LinkageError failure) {
            if (NELogConfig.AUXILIARY_DEBUG) LoggerFactory.getLogger("neoecoae/keyreuse").debug(
                "Cannot protect ECO key view for {}; retaining uncached addon keys", targetClassName, failure);
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
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
