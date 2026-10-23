package cn.dancingsnow.neoecoae.util;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;

import java.util.List;

import static org.mockito.Mockito.*;

/** Supplies registry/channel initialization boundaries normally installed by the Forge launcher. */
public final class InventoryTestBootstrap {
    private static boolean keyRegistryInitialized;
    private InventoryTestBootstrap() {}

    public static void initialize() {
        SharedConstants.tryDetectVersion();
        try (var capabilities = mockStatic(net.minecraftforge.common.capabilities.CapabilityManager.class)) {
            capabilities.when(() -> net.minecraftforge.common.capabilities.CapabilityManager.get(any()))
                .thenAnswer(call -> mock(net.minecraftforge.common.capabilities.Capability.class));
            Class.forName("net.minecraftforge.common.capabilities.ForgeCapabilities");
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Cannot initialize test capabilities", failure);
        }
        try (var loading = mockStatic(LoadingModList.class);
             var network = mockStatic(net.minecraftforge.network.NetworkHooks.class)) {
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loading.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
            // Forge normally opens these registries while constructing mod content.
            for (var registry : net.minecraft.core.registries.BuiltInRegistries.REGISTRY) {
                if (registry instanceof net.minecraft.core.MappedRegistry<?> mapped) mapped.unfreeze();
            }
            if (net.minecraftforge.registries.ForgeRegistries.ITEMS instanceof net.minecraftforge.registries.ForgeRegistry<?> items) items.unfreeze();
            if (net.minecraftforge.registries.ForgeRegistries.FLUIDS instanceof net.minecraftforge.registries.ForgeRegistry<?> fluids) fluids.unfreeze();
            if (net.minecraftforge.registries.ForgeRegistries.BLOCKS instanceof net.minecraftforge.registries.ForgeRegistry<?> blocks) blocks.unfreeze();
            if (net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES instanceof net.minecraftforge.registries.ForgeRegistry<?> types) types.unfreeze();
            if (net.minecraftforge.common.crafting.CraftingHelper.getID(net.minecraftforge.common.crafting.VanillaIngredientSerializer.INSTANCE) == null)
                net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation("minecraft", "item"), net.minecraftforge.common.crafting.VanillaIngredientSerializer.INSTANCE);
            if (net.minecraftforge.common.crafting.CraftingHelper.getID(net.minecraftforge.common.crafting.StrictNBTIngredient.Serializer.INSTANCE) == null)
                net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation("forge", "nbt"), net.minecraftforge.common.crafting.StrictNBTIngredient.Serializer.INSTANCE);
            if (net.minecraftforge.common.crafting.CraftingHelper.getID(net.minecraftforge.common.crafting.PartialNBTIngredient.Serializer.INSTANCE) == null)
                net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation("forge", "partial_nbt"), net.minecraftforge.common.crafting.PartialNBTIngredient.Serializer.INSTANCE);
            cn.dancingsnow.neoecoae.compat.ae2.AE2PatternOptimizationIntegration.init();
            if (!keyRegistryInitialized) {
                appeng.api.stacks.AEKeyTypesInternal.setRegistry(() -> LargeWorkstationTestKeys.REGISTRY);
                keyRegistryInitialized = true;
            }
            cn.dancingsnow.neoecoae.compat.ECOPatternIntegrations.initialize();
        }
    }
}
