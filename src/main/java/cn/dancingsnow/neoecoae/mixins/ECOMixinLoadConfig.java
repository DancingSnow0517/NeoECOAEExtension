package cn.dancingsnow.neoecoae.mixins;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.slf4j.LoggerFactory;

/**
 * 在这里集中管理所有的mixin加载，禁止任何的独立模块，我希望ai能够读到之后好好看看
 */
public final class ECOMixinLoadConfig implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE = "cn.dancingsnow.neoecoae.mixins.";
    private static final String THUNDERBOLT_MIXINS = "compat.thunderbolt.";
    private static final String OMNI_SEQUENCE_MIXINS = "compat.omnisequence.";
    private static final String KEY_REUSE_MIXINS = "ae2.keyreuse.";
    private static final Map<String, List<String>> REQUIRED_MODS = Map.of(
        "compat.advancedae.", List.of("advanced_ae"),
        "compat.ae2omnicells.", List.of("ae2omnicells"),
        "compat.appbot.", List.of("appbot", "botania"),
        "compat.extendedae.", List.of("extendedae"),
        "compat.fluxnetworks.", List.of("fluxnetworks"),
        "compat.merequester.", List.of("merequester"),
        OMNI_SEQUENCE_MIXINS, List.of("molecularmanipulator"),
        THUNDERBOLT_MIXINS, List.of("thunderbolt"),
        "jei.client.", List.of("jei")
    );
    private static final String THUNDERBOLT_API = "com/moakiee/thunderbolt/api/crafting/";
    private static final String PLANNING_REQUEST = "L" + THUNDERBOLT_API + "PlanningRequest;";
    private static final String PLANNING_CAPTURE = "(Lappeng/api/networking/IGrid;" + PLANNING_REQUEST
        + ")Ljava/lang/Object;";
    private static final Set<String> AE2_FAST_PATH_VERSIONS = Set.of("19.2.17", "19.2.18", "19.2.19", "19.2.20");
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
                && AE2_FAST_PATH_VERSIONS.contains(mod.getVersion().toString()));
        }

        for (var requirement : REQUIRED_MODS.entrySet()) {
            if (mixin.startsWith(requirement.getKey())
                    && !requirement.getValue().stream().allMatch(ECOMixinLoadConfig::isModLoaded)) {
                return false;
            }
        }

        if (mixin.startsWith(THUNDERBOLT_MIXINS)) {
            return supportsThunderboltMixin(targetClassName, mixin.substring(THUNDERBOLT_MIXINS.length()));
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
        return !mixin.startsWith(OMNI_SEQUENCE_MIXINS) || supportsOmniBatchApi();
    }

    private static boolean isModLoaded(String modId) {
        var mods = LoadingModList.get();
        return mods != null && mods.getMods().stream().anyMatch(mod -> mod.getModId().equals(modId));
    }

    private static boolean supportsThunderboltMixin(String targetClassName, String mixin) {
        String target = targetClassName.replace('.', '/') + ".class";
        return switch (mixin) {
            case "ThunderboltPlanningEngineMixin" -> hasMethod(THUNDERBOLT_API + "CraftingPlanningEngine.class",
                "capture", PLANNING_CAPTURE)
                && hasMethod(target, "createSession", "(" + PLANNING_REQUEST + "Ljava/lang/Object;L"
                    + THUNDERBOLT_API + "PlanningAttemptContext;)L" + THUNDERBOLT_API + "PlanningEngineSession;")
                && !hasMethod(target, "capture", PLANNING_CAPTURE);
            case "ECOProcessingPatternFuzzyMixin" -> hasMethod(
                "com/moakiee/thunderbolt/core/crafting/pattern/FuzzyPatternInputs.class",
                "acceptsSameIdVariants", "(I)Z");
            case "Ae2LtIOPortBlockEntityMixin", "Ae2LtIOPortMenuMixin", "Ae2LtTimeWheelCraftingCpuLogicMixin" ->
                hasClassResource(target);
            case "Ae2LtWorkstationWirelessMixin" ->
                hasMethod(target, "canKeepVirtualConnection", "(Lappeng/api/networking/IGridNode;)Z");
            case "ECOThunderboltWorkstationProviderMixin" ->
                hasClassResource(THUNDERBOLT_API + "batch/IBatchCraftingProvider.class")
                    && hasClassResource(THUNDERBOLT_API + "batch/BatchJobView.class");
            default -> false;
        };
    }

    private static boolean hasClassResource(String resource) {
        return ECOMixinLoadConfig.class.getClassLoader().getResource(resource) != null;
    }

    private static boolean hasMethod(String resource, String name, String descriptor) {
        try (var input = ECOMixinLoadConfig.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return false;
            }
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
        } catch (IOException | RuntimeException unavailable) {
            return false;
        }
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
            LoggerFactory.getLogger("neoecoae/keyreuse").debug(
                "Cannot protect ECO key view for {}; retaining uncached addon keys", targetClassName, failure);
            return false;
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
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

/*
生死看淡，不服就干
子类覆盖，纯属扯淡
管他什么，mixin进去
遇到冲突，mixin进去
遇到失效，mixin进去
注入点相似，不崩溃就行
注入点冲突，拉入黑名单
注入点冲突，上mixin squared
被干扰行为，上mixin squared
mixin被取消了，上mixinruntime
mixin无法注入，上mixinruntime
左手redirect，右手overwrite
左手inject，右手targethandler
左手mixin squared，右手反射sp mixin
mixin不够用，直接上coremod
coremod不够用，直接上javaagent
javaagent不够用，直接上asm
 */
