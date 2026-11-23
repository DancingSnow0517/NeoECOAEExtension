package cn.dancingsnow.neoecoae.mixins;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

public final class ECOMixinLoadConfig implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE = "cn.dancingsnow.neoecoae.mixins.";
    private static final String THUNDERBOLT_MIXINS = "compat.thunderbolt.";
    private static final String THUNDERBOLT_BATCH_API = "com/moakiee/thunderbolt/api/crafting/batch/";
    private static final String KEY_REUSE_MIXINS = "ae2.keyreuse.";
    private static final Map<String, List<String>> REQUIRED_MODS = Map.of(
        "compat.advancedae.", List.of("advanced_ae"),
        "compat.ae2omnicells.", List.of("ae2omnicells"),
        "compat.appbot.", List.of("appbot", "botania"),
        "compat.extendedae.", List.of("extendedae"),
        "compat.fluxnetworks.", List.of("fluxnetworks"),
        "compat.merequester.", List.of("merequester"),
        "jei.client.", List.of("jei")
    );
    private static final Set<String> AE2_VERSION_CHECKED = Set.of(
        "ae2.accessor.PrecisePriorityListAccessor",
        "ae2.key.DefaultKeyOwnerMixin",
        "ae2.key.AEItemKeyFactoryMixin",
        "ae2.key.AEFluidKeyFactoryMixin",
        "ae2.storage.KeyCounterSnapshotMixin",
        "ae2.storage.DelegatingStorageListingMixin",
        "ae2.storage.MEInventoryHandlerListingMixin"
    );

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String mixin = mixinClassName.substring(MIXIN_PACKAGE.length());
        if (AE2_VERSION_CHECKED.contains(mixin)) {
            var mods = LoadingModList.get();
            return mods != null && mods.getMods().stream().anyMatch(mod -> mod.getModId().equals("ae2")
                && mod.getVersion().toString().equals("26.1.13-beta"));
        }

        for (var requirement : REQUIRED_MODS.entrySet()) {
            if (mixin.startsWith(requirement.getKey())
                    && !requirement.getValue().stream().allMatch(ECOMixinLoadConfig::isModLoaded)) {
                return false;
            }
        }

        if (mixin.startsWith(THUNDERBOLT_MIXINS)) {
            return switch (mixin.substring(THUNDERBOLT_MIXINS.length())) {
                case "Ae2LtIOPortBlockEntityMixin" ->
                    hasClassResource("com/moakiee/ae2lt/blockentity/OverloadedIOPortBlockEntity.class");
                case "Ae2LtIOPortMenuMixin" ->
                    hasClassResource("com/moakiee/ae2lt/menu/OverloadedIOPortMenu.class");
                case "Ae2LtTimeWheelCraftingCpuLogicMixin" ->
                    hasClassResource("com/moakiee/ae2lt/crafting/timewheel/Ae2LtTimeWheelCraftingCpuLogic.class");
                case "ECOThunderboltWorkstationProviderMixin" ->
                    hasClassResource(THUNDERBOLT_BATCH_API + "IBatchCraftingProvider.class")
                        && hasClassResource(THUNDERBOLT_BATCH_API + "BatchJobView.class");
                default -> false;
            };
        }
        if (mixin.startsWith(KEY_REUSE_MIXINS)) {
            return switch (mixin.substring(KEY_REUSE_MIXINS.length())) {
                case "MatterKeyReuseMixin" -> supportsSharedKey(targetClassName,
                    "Lcom/buuz135/replication/api/matter_fluid/MatterStack;", false);
                case "MekanismKeyReuseMixin" -> supportsSharedKey(targetClassName,
                    "Lmekanism/api/chemical/ChemicalStack;", true);
                default -> false;
            };
        }
        return !mixin.startsWith("compat.omnisequence.") || supportsOmniBatchApi();
    }

    private static boolean isModLoaded(String modId) {
        var mods = LoadingModList.get();
        return mods != null && mods.getMods().stream().anyMatch(mod -> mod.getModId().equals(modId));
    }

    private static boolean hasClassResource(String resource) {
        return ECOMixinLoadConfig.class.getClassLoader().getResource(resource) != null;
    }

    private static boolean supportsOmniBatchApi() {
        try {
            var api = Class.forName("com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingApi",
                false, ECOMixinLoadConfig.class.getClassLoader());
            return (int) api.getMethod("apiVersion").invoke(null) == 1;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }

    private static boolean supportsSharedKey(String targetClassName, String stackType, boolean checkHashCode) {
        try {
            var node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            boolean field = node.fields.stream().anyMatch(value -> value.name.equals("stack")
                && value.desc.equals(stackType) && (value.access & Opcodes.ACC_STATIC) == 0);
            boolean getter = node.methods.stream().anyMatch(method -> method.name.equals("getStack")
                && method.desc.equals("()" + stackType) && (method.access & Opcodes.ACC_STATIC) == 0);
            boolean hashCode = !checkHashCode || node.methods.stream().anyMatch(method ->
                method.name.equals("hashCode") && method.desc.equals("()I"));
            return field && getter && hashCode;
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Exception | LinkageError failure) {
            LoggerFactory.getLogger("neoecoae/keyreuse").warn(
                "Cannot protect ECO key view for {}; retaining uncached addon keys", targetClassName, failure);
            return false;
        }
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
