package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Internal-layout bridges are enabled only for the dependency whose bytecode and listing contracts were checked. */
public final class StoragePerformanceMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> CHECKED = Set.of("KeyCounterSnapshotMixin", "DelegatingStorageListingMixin",
        "MEInventoryHandlerListingMixin", "PrecisePriorityListAccessor", "DefaultKeyOwnerMixin",
        "AEItemKeyFactoryMixin", "AEFluidKeyFactoryMixin");

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!CHECKED.contains(mixin.substring(mixin.lastIndexOf('.') + 1))) return true;
        var mods = LoadingModList.get();
        return mods != null && mods.getMods().stream().anyMatch(mod -> mod.getModId().equals("ae2")
            && mod.getVersion().toString().equals("19.2.18"));
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
