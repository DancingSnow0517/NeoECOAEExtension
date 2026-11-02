package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationRecipeAdapters;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddonWorkstationApiTest {
    private static final ResourceLocation TYPE = id("processing"), MODULE = id("module"), ADAPTER = id("adapter");
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void customCardControlsRecipeDiscoveryExecutionSelectionAndCacheInvalidation() {
        boolean previous = NEConfig.largeWorkstationIgnoreUpgradeCards;
        NEConfig.largeWorkstationIgnoreUpgradeCards = false;
        var manager = mock(RecipeManager.class);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Recipe<?> external = mock(Recipe.class);
        doReturn(RecipeType.simple(TYPE)).when(external).getType();
        var holder = new RecipeHolder<>(id("recipe"), external);
        when(manager.getRecipes()).thenReturn(List.of(holder));
        Level level = mock(Level.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(level.registryAccess()).thenReturn(registries);
        assertTrue(LargeWorkstationRecipes.getAll(manager, registries).isEmpty());
        var module = module();
        ECOWorkstationModules.register(0, module);
        ECOWorkstationRecipeAdapters.register(ADAPTER, 0, TYPE, (recipe, lookup) -> adapted(recipe.id(), TYPE));
        try {
            assertTrue(LargeWorkstationModule.isUpgradeCard(new ItemStack(Items.DIAMOND)));
            assertEquals(MODULE, ECOWorkstationModules.fromCard(new ItemStack(Items.DIAMOND)).id());
            assertEquals(LargeWorkstationModule.OTHER, LargeWorkstationModule.fromUpgradeCard(new ItemStack(Items.DIAMOND)));
            var all = LargeWorkstationRecipes.getAll(manager, registries);
            assertEquals(1, all.size());
            assertSame(all, LargeWorkstationRecipes.getAll(manager, registries));
            assertEquals(4321, all.getFirst().energy());
            assertEquals(50, all.getFirst().extraInputs().getFirst().amount());
            assertTrue(LargeWorkstationRecipes.getAvailable(level, ECOWorkstationModules.builtin(LargeWorkstationModule.REGULAR).id()).isEmpty());
            assertEquals(all, LargeWorkstationRecipes.getAvailable(level, MODULE));
            assertSame(module, ECOWorkstationModules.fromSourceType(TYPE));
            assertThrows(IllegalArgumentException.class, () -> ECOWorkstationModules.register(5, module));
            assertTrue(ECOWorkstationRecipeAdapters.unregister(ADAPTER));
            assertTrue(LargeWorkstationRecipes.getAll(manager, registries).isEmpty());
            assertTrue(LargeWorkstationRecipes.getAvailable(level, MODULE).isEmpty());
        } finally {
            ECOWorkstationRecipeAdapters.unregister(ADAPTER);
            ECOWorkstationModules.unregister(MODULE);
            NEConfig.largeWorkstationIgnoreUpgradeCards = previous;
        }
    }

    @Test void adaptersCannotChangeRecipeIdentityOrSilentlyDropTheirCostsOnFailure() {
        var holder = new RecipeHolder<>(id("recipe"), mock(Recipe.class));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        ECOWorkstationRecipeAdapters.register(ADAPTER, 0, TYPE, (recipe, lookup) -> adapted(id("different"), TYPE));
        try {
            assertThrows(IllegalArgumentException.class, () -> ECOWorkstationRecipeAdapters.adapt(TYPE, holder, registries));
        } finally { ECOWorkstationRecipeAdapters.unregister(ADAPTER); }
        ECOWorkstationRecipeAdapters.register(ADAPTER, 0, TYPE, (recipe, lookup) -> { throw new IllegalStateException("Unsupported cost"); });
        try {
            assertThrows(IllegalStateException.class, () -> ECOWorkstationRecipeAdapters.adapt(TYPE, holder, registries));
        } finally { ECOWorkstationRecipeAdapters.unregister(ADAPTER); }
    }

    private static ECOWorkstationModules.Module module() {
        return new ECOWorkstationModules.Module(MODULE, ResourceLocation.parse("minecraft:diamond"), Set.of(TYPE),
            Component.literal("Addon processing"), Component.literal("Addon"), 0x123456,
            id("textures/module.png"), id("textures/circuit.png"), id("block/idle"), id("block/working"));
    }
    private static LargeWorkstationRecipe adapted(ResourceLocation id, ResourceLocation source) {
        var display = new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), new ItemStack(Items.GOLD_INGOT), FluidStack.EMPTY, 4321);
        return new LargeWorkstationRecipe(id, display, 4321, List.of(new GenericStack(AEFluidKey.of(Fluids.LAVA), 50)), source);
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("addon_workstation_test", path); }
}
