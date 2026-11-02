package cn.dancingsnow.neoecoae.api.workstation;

import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Additive module IDs; existing enum values and saved block states keep their original meaning. */
public final class ECOWorkstationModules {
    private static final ECOExtensionRegistry<Module> REGISTRY = new ECOExtensionRegistry<>();
    private ECOWorkstationModules() {}
    public record Module(ResourceLocation id, ResourceLocation upgradeCard, Set<ResourceLocation> sourceTypes,
                         Component title, Component shortTitle, int color,
                         @Nullable ResourceLocation icon, @Nullable ResourceLocation circuit,
                         @Nullable ResourceLocation idleModel, @Nullable ResourceLocation workingModel) {
        public Module {
            Objects.requireNonNull(id); Objects.requireNonNull(upgradeCard);
            sourceTypes = Set.copyOf(sourceTypes);
            title = Objects.requireNonNull(title).copy(); shortTitle = Objects.requireNonNull(shortTitle).copy();
        }
        public boolean custom() { return find(id) != null; }
        public LargeWorkstationModule legacy() { return custom() ? LargeWorkstationModule.OTHER : LargeWorkstationModule.byName(id.getPath()); }
    }

    public static void register(int priority, Module module) {
        if (module.id().getNamespace().equals("neoecoae")) throw new IllegalArgumentException("Use an addon namespace for modules");
        REGISTRY.register(module.id(), priority, module);
    }
    public static boolean unregister(ResourceLocation id) { return REGISTRY.unregister(id); }
    public static long revision() { return REGISTRY.revision(); }
    public static List<Module> registered() { return REGISTRY.entries().stream().map(ECOExtensionRegistry.Entry::extension).toList(); }
    @Nullable public static Module find(@Nullable ResourceLocation id) {
        for (var entry : REGISTRY.entries()) if (entry.id().equals(id)) return entry.extension();
        return null;
    }
    public static Module builtin(LargeWorkstationModule module) {
        return new Module(ResourceLocation.fromNamespaceAndPath("neoecoae", module.getSerializedName()), module.upgradeCardId(),
            Set.of(), module.title(), module.shortTitle(), module.color(), null, null, null, null);
    }
    public static Module resolve(@Nullable ResourceLocation id) {
        Module custom = find(id);
        if (custom != null) return custom;
        return builtin(id != null && "neoecoae".equals(id.getNamespace()) ? LargeWorkstationModule.byName(id.getPath())
            : LargeWorkstationModule.REGULAR);
    }
    @Nullable public static Module customFromCard(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (var entry : REGISTRY.entries()) if (entry.extension().upgradeCard().equals(item)) return entry.extension();
        return null;
    }
    public static Module fromCard(ItemStack stack) {
        Module custom = customFromCard(stack);
        return custom != null ? custom : builtin(LargeWorkstationModule.fromUpgradeCard(stack));
    }

    /** Keeps legacy controller overrides effective when an integration only implements the enum view. */
    public static Module installed(@Nullable cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity host) {
        if (host == null) return builtin(LargeWorkstationModule.REGULAR);
        Module description = host.getInstalledModuleDescription();
        if (description != null) return description;
        LargeWorkstationModule legacy = host.getInstalledModule();
        return builtin(legacy == null ? LargeWorkstationModule.REGULAR : legacy);
    }
    public static Module fromSourceType(ResourceLocation type) {
        for (var entry : REGISTRY.entries()) if (entry.extension().sourceTypes().contains(type)) return entry.extension();
        return builtin(LargeWorkstationModule.fromSourceType(type));
    }
    public static boolean unlocks(ResourceLocation installed, ResourceLocation sourceType) {
        Module source = fromSourceType(sourceType);
        if (source.custom()) return source.id().equals(installed);
        return resolve(installed).legacy().unlocks(sourceType);
    }
}
