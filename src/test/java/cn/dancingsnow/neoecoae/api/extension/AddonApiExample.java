package cn.dancingsnow.neoecoae.api.extension;

import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.capability.ECOFeature;
import cn.dancingsnow.neoecoae.api.computation.IECOComputationCellItem;
import cn.dancingsnow.neoecoae.api.multiblock.*;
import cn.dancingsnow.neoecoae.api.power.ECOPowerProfiles;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageChannelDisplays;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationRecipeAdapters;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Compiled integration skeleton; replace example IDs and supply your registered block/entity types. */
@SuppressWarnings("unused")
public final class AddonApiExample {
    private AddonApiExample() {}

    public static void commonSetup(BlockEntityType<?> hostType, Block drive, IECOTier tier, MultiBlockDefinition definition,
                                    Block originalCasing, Block addonCasing, ECOWorkstationRecipeAdapters.Adapter recipeAdapter) {
        ECOPowerProfiles.register(id("power"), 100, context -> context.host().getType() == hostType
            ? context.defaultPower() / 8 : null);
        ECOMultiblockDefinitions.register(id("structure"), 100, context -> context.host().getType() == hostType
            && context.kind() == ECOMachineKind.COMPUTATION ? definition : null);
        ECOMultiblockParts.register(id("drive"), 100, context -> context.state().is(drive)
            ? new ECOMultiblockParts.Part(Set.of(ECOMultiblockParts.Role.COMPUTATION_DRIVE), tier,
                ECOMultiblockParts.DriveRow.UPPER, -10) : null);
        ECOMultiblockRequirements.registerAlternatives(id("casings"), 100, originalCasing, Set.of(addonCasing));
        ECOStorageChannelDisplays.register(id("resource_display"), 100,
            new ECOStorageChannelDisplays.Display(id("resource"), Set.of(id("bulk_resource")), Component.literal("Resource"),
                0x78C9A4, id("textures/gui/resource.png"), id("textures/gui/resource_bar.png"), 10));
        ECOWorkstationRecipeAdapters.register(id("recipe_adapter"), 100, id("processing"), recipeAdapter);
        ECOWorkstationModules.register(100, new ECOWorkstationModules.Module(id("processing_module"), id("module_card"),
            Set.of(id("processing")), Component.literal("Addon processing"), Component.literal("Addon"), 0x78C9A4,
            id("textures/gui/module.png"), id("textures/gui/circuit.png"), id("block/workstation_idle"), id("block/workstation_working")));
    }

    /** Call before model baking on the client, once the block registration is available. */
    public static void clientModels(IECOTier tier, Block drive) {
        ECOComputationModels.registerCableModel(tier, drive, id("block/cable_off"), id("block/cable_on"));
    }

    public abstract static class AddonTier implements IECOTier {
        @Override public double getComponentIdlePower() { return 2; }
        @Override public boolean supportsFeature(ECOFeature feature) {
            return feature == ECOFeature.NETWORK_SWITCH || IECOTier.super.supportsFeature(feature);
        }
    }

    public static final class MemoryItem extends Item implements IECOComputationCellItem {
        private final IECOTier tier;
        public MemoryItem(Properties properties, IECOTier tier) { super(properties); this.tier = tier; }
        @Override public IECOTier getTier() { return tier; }
        @Override public long getComputationBytes(ItemStack stack) { return tier.getCPUTotalBytes() * 2; }
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("example_addon", path); }
}
