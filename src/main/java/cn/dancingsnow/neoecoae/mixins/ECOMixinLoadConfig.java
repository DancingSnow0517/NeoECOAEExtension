package cn.dancingsnow.neoecoae.mixins;

import cn.dancingsnow.neoecoae.config.NELogConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
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
    private static final String KEY_REUSE_MIXINS = "ae2.keyreuse.";
    private static final Map<String, List<String>> REQUIRED_MODS = Map.of(
        "advanced_ae.", List.of("advanced_ae"),
        "ae2_pattern_disk.", List.of("ae2_pattern_disk"),
        "ae2omnicells.", List.of("ae2omnicells"),
        "appbot.", List.of("appbot", "botania"),
        "extendedae.", List.of("extendedae"),
        "fluxnetworks.", List.of("fluxnetworks"),
        "merequester.", List.of("merequester"),
        "thunderbolt.", List.of("thunderbolt"),
        "jei.client.", List.of("jei")
    );
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

        if (mixin.startsWith(KEY_REUSE_MIXINS)) {
            return switch (mixin.substring(KEY_REUSE_MIXINS.length())) {
                case "MatterKeyReuseMixin" -> supportsSharedKey(targetClassName,
                    "Lcom/buuz135/replication/api/matter_fluid/MatterStack;", false);
                case "MekanismKeyReuseMixin" -> supportsSharedKey(targetClassName,
                    "Lmekanism/api/chemical/ChemicalStack;", true);
                default -> false;
            };
        }
        return true;
    }

    private static boolean isModLoaded(String modId) {
        var mods = LoadingModList.get();
        return mods != null && mods.getMods().stream().anyMatch(mod -> mod.getModId().equals(modId));
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
            if (NELogConfig.AUXILIARY_DEBUG) LoggerFactory.getLogger("neoecoae/keyreuse").debug(
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
