package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.DataComponentIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.crafting.DataComponentFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LargeWorkstationRecipeDeduplicationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void crossModAdaptersKeepCheaperRecipeAndItsOriginalType() {
        var extended = decode("extendedae:assembler", "extendedae:crystal_assembler", """
            {"input_items":[{"ingredient":{"item":"minecraft:iron_ingot"},"amount":4}],
             "output":{"id":"minecraft:diamond","count":2}}
            """);
        var advanced = new LargeWorkstationRecipe(new ResourceLocation("advanced_ae:reaction"), extended.display(),
            1000, List.of(), new ResourceLocation("advanced_ae:reaction"));
        assertEquals(List.of(advanced), deduplicate(extended, advanced));
        assertEquals(List.of(advanced), deduplicate(advanced, extended));
        var label = assertInstanceOf(TranslatableContents.class, advanced.sourceTypeDescription().getContents());
        assertEquals("gui.neoecoae.large_integrated_working_station.recipe_type", label.getKey());
        var typeName = assertInstanceOf(Component.class, label.getArgs()[0]);
        assertEquals("recipe_type.neoecoae.advanced_ae.reaction",
            assertInstanceOf(TranslatableContents.class, typeName.getContents()).getKey());
    }

    @Test void lockedRecipeFamiliesCannotHideUnlockedRecipesDuringDeduplication() {
        var regular = materialRecipe("test:regular", 100, List.of());
        var advanced = new LargeWorkstationRecipe(new ResourceLocation("advanced_ae:cheaper"), regular.display(),
            50, List.of(), new ResourceLocation("advanced_ae:reaction"));
        var extended = new LargeWorkstationRecipe(new ResourceLocation("expatternprovider:cheapest"), regular.display(),
            10, List.of(), new ResourceLocation("expatternprovider:crystal_assembler"));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = JsonOps.INSTANCE;
        var recipes = List.of(extended, regular, advanced);
        assertEquals(List.of(regular), LargeWorkstationRecipes.selectForModule(recipes, LargeWorkstationModule.REGULAR, ops));
        assertEquals(List.of(advanced), LargeWorkstationRecipes.selectForModule(recipes, LargeWorkstationModule.ADVANCED_AE, ops));
        assertEquals(List.of(extended), LargeWorkstationRecipes.selectForModule(recipes, LargeWorkstationModule.EXTENDED_AE, ops));
    }

    @Test void fourCardsUnlockTheirFamilyAndRejectExcludedAndUnknownSources() {
        var sources = java.util.Map.of(
            LargeWorkstationModule.ADVANCED_AE, new ResourceLocation("advanced_ae:reaction"),
            LargeWorkstationModule.EXTENDED_AE, new ResourceLocation("expatternprovider:crystal_assembler"),
            LargeWorkstationModule.LIGHTNING_TECH, new ResourceLocation("ae2lt:lightning_assembly"));
        for (var installed : LargeWorkstationModule.values()) {
            assertTrue(installed.unlocks(LargeWorkstationRecipe.NATIVE_TYPE));
            for (String source : List.of("test:uncarded_adapter", "extendedae_plus:crystal_assembler_plus",
                    "ae2cs:circuit_etcher_recipe", "data_energistics:data_reassembler", "appgen:synthesizing")) {
                assertFalse(installed.unlocks(new ResourceLocation(source)));
            }
            for (var entry : sources.entrySet()) assertEquals(installed == entry.getKey(), installed.unlocks(entry.getValue()));
        }
        assertTrue(LargeWorkstationModule.EXTENDED_AE.unlocks(new ResourceLocation("extendedae:crystal_assembler")));
        assertEquals(4, java.util.Arrays.stream(LargeWorkstationModule.values()).filter(LargeWorkstationModule::hasUpgradeCard).count());
        assertFalse(LargeWorkstationModule.isUpgradeCard(new ItemStack(Items.PAPER)));
        assertEquals(LargeWorkstationModule.REGULAR, LargeWorkstationModule.fromUpgradeCard(ItemStack.EMPTY));
    }

    @Test void inputOrderAlternativeOrderAndSplitStacksDoNotCreateDuplicates() {
        var first = recipe("test:a", List.of(
            input(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 4), input(Ingredient.of(Items.STONE), 2)),
            emptyFluid(), new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 200, List.of());
        var cheaper = recipe("test:z", List.of(input(Ingredient.of(Items.STONE), 2),
            input(Ingredient.of(Items.GOLD_INGOT, Items.IRON_INGOT), 1),
            input(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 3)),
            emptyFluid(), new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 100, List.of());
        var narrower = recipe("test:narrower", List.of(input(Ingredient.of(Items.IRON_INGOT), 4),
            input(Ingredient.of(Items.STONE), 2)), emptyFluid(), new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 50, List.of());
        assertEquals(List.of(narrower, cheaper), deduplicate(first, cheaper, narrower));
    }

    @Test void lightningHasPriorityThenAeCostThenLightningAmount() {
        var base = materialRecipe("test:plain", 1, List.of());
        var high = com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE;
        var expensive = materialRecipe("test:expensive", 1000, List.of(new GenericStack(high, 1)));
        var cheaper = materialRecipe("test:cheaper", 500, List.of(new GenericStack(high, 4)));
        var fewer = materialRecipe("test:fewer", 500, List.of(new GenericStack(high, 2)));
        assertEquals(List.of(cheaper), deduplicate(base, expensive, cheaper));
        assertEquals(List.of(fewer), deduplicate(base, expensive, cheaper, fewer));
        assertEquals(List.of(fewer), deduplicate(fewer, cheaper, expensive, base));
    }

    @Test void equalCostsHaveStableRecipeIdTieBreaker() {
        var first = materialRecipe("test:a", 100, List.of());
        var second = materialRecipe("test:z", 100, List.of());
        assertEquals(List.of(first), deduplicate(first, second));
        assertEquals(List.of(first), deduplicate(second, first));
    }

    @Test void fluidTypeAndInputOutputAmountsRemainPartOfTheContract() {
        var inputs = List.of(input(Ingredient.of(Items.IRON_INGOT), 4));
        var first = recipe("test:a", inputs, new SizedFluidIngredient(FluidIngredient.of(Fluids.WATER), 1000),
            new ItemStack(Items.DIAMOND), new FluidStack(Fluids.LAVA, 250), 100, List.of());
        var alternatives = recipe("test:b", inputs, new SizedFluidIngredient(FluidIngredient.of(Fluids.WATER, Fluids.LAVA), 1000),
            new ItemStack(Items.DIAMOND), new FluidStack(Fluids.LAVA, 250), 100, List.of());
        var inputAmount = recipe("test:c", inputs, new SizedFluidIngredient(FluidIngredient.of(Fluids.WATER), 1001),
            new ItemStack(Items.DIAMOND), new FluidStack(Fluids.LAVA, 250), 100, List.of());
        var outputAmount = recipe("test:d", inputs, first.display().inputFluid(),
            new ItemStack(Items.DIAMOND), new FluidStack(Fluids.LAVA, 251), 100, List.of());
        var outputType = recipe("test:e", inputs, first.display().inputFluid(),
            new ItemStack(Items.DIAMOND), new FluidStack(Fluids.WATER, 250), 100, List.of());
        assertEquals(List.of(first, alternatives, inputAmount, outputAmount, outputType),
            deduplicate(outputType, first, inputAmount, outputAmount, alternatives));
    }

    @Test void absentFluidIgnoresItsPlaceholderAmount() {
        var first = materialRecipe("test:a", 100, List.of());
        var cheaper = recipe("test:b", first.display().inputItems(), new SizedFluidIngredient(FluidIngredient.empty(), 1000),
            first.display().itemOutput().copy(), FluidStack.EMPTY, 50, List.of());
        assertEquals(List.of(cheaper), deduplicate(first, cheaper));
    }

    @Test void outputCountsAndComponentsRemainDistinct() {
        var first = materialRecipe("test:a", 100, List.of());
        var named = new ItemStack(Items.DIAMOND);
        named.setHoverName(Component.literal("Distinct output"));
        var namedOutput = recipe("test:b", first.display().inputItems(), emptyFluid(), named, FluidStack.EMPTY, 1, List.of());
        var largerOutput = recipe("test:c", first.display().inputItems(), emptyFluid(), new ItemStack(Items.DIAMOND, 2),
            FluidStack.EMPTY, 1, List.of());
        assertEquals(List.of(first, namedOutput, largerOutput), deduplicate(first, namedOutput, largerOutput));
    }

    @Test void componentItemInputsDeduplicateOnlyWhenPredicatesAgree() {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.setHoverName(Component.literal("Required name"));
        var first = recipe("test:a", List.of(input(DataComponentIngredient.of(true, stack), 4)), emptyFluid(),
            new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 100, List.of());
        var cheaper = recipe("test:b", List.of(input(DataComponentIngredient.of(true, stack.copy()), 4)), emptyFluid(),
            new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 50, List.of());
        var partial = recipe("test:c", List.of(input(DataComponentIngredient.of(false, stack), 4)), emptyFluid(),
            new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 50, List.of());
        var plain = materialRecipe("test:d", 50, List.of());
        assertEquals(List.of(cheaper, partial, plain), deduplicate(first, cheaper, partial, plain));
    }

    @Test void componentFluidInputsAndOutputsRemainDistinct() {
        var inputs = List.of(input(Ingredient.of(Items.IRON_INGOT), 4));
        var stack = new FluidStack(Fluids.WATER, 1000);
        stack.setHoverName(Component.literal("Required fluid"));
        var first = recipe("test:a", inputs, new SizedFluidIngredient(DataComponentFluidIngredient.of(true, stack), 1000),
            ItemStack.EMPTY, new FluidStack(Fluids.LAVA, 250), 100, List.of());
        var cheaper = recipe("test:b", inputs, new SizedFluidIngredient(DataComponentFluidIngredient.of(true, stack.copy()), 1000),
            ItemStack.EMPTY, new FluidStack(Fluids.LAVA, 250), 50, List.of());
        var partial = recipe("test:c", inputs, new SizedFluidIngredient(DataComponentFluidIngredient.of(false, stack), 1000),
            ItemStack.EMPTY, new FluidStack(Fluids.LAVA, 250), 50, List.of());
        var namedOutput = new FluidStack(Fluids.LAVA, 250);
        namedOutput.setHoverName(Component.literal("Distinct output"));
        var output = recipe("test:d", inputs, cheaper.display().inputFluid(), ItemStack.EMPTY, namedOutput, 50, List.of());
        assertEquals(List.of(cheaper, partial, output), deduplicate(first, cheaper, partial, output));
    }

    private static List<LargeWorkstationRecipe> deduplicate(LargeWorkstationRecipe... recipes) {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        return LargeWorkstationRecipes.deduplicate(List.of(recipes), JsonOps.INSTANCE);
    }

    private static LargeWorkstationRecipe decode(String id, String type, String json) {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        return LargeWorkstationRecipes.decode(new ResourceLocation(id), type, JsonParser.parseString(json).getAsJsonObject(),
            JsonOps.INSTANCE);
    }

    private static SizedIngredient input(Ingredient ingredient, int amount) { return new SizedIngredient(ingredient, amount); }
    private static SizedFluidIngredient emptyFluid() { return new SizedFluidIngredient(FluidIngredient.empty(), 1); }

    private static LargeWorkstationRecipe materialRecipe(String id, long energy, List<GenericStack> extras) {
        return recipe(id, List.of(input(Ingredient.of(Items.IRON_INGOT), 4)), emptyFluid(), new ItemStack(Items.DIAMOND),
            FluidStack.EMPTY, energy, extras);
    }

    private static LargeWorkstationRecipe recipe(String id, List<SizedIngredient> items, SizedFluidIngredient fluid,
        ItemStack output, FluidStack fluidOutput, long energy, List<GenericStack> extras) {
        return new LargeWorkstationRecipe(new ResourceLocation(id), new IntegratedWorkingStationRecipe(items, fluid, output,
            fluidOutput, (int) Math.min(Integer.MAX_VALUE, energy)), energy, extras);
    }
}
