package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationIngredient;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingIngredient;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;
import net.pedroksl.ae2addonlib.recipes.IngredientStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ForgeRecipeAdapterTest {
    private static final ResourceLocation ID = new ResourceLocation("test", "adapter");
    @BeforeAll static void bootstrap() { PlatformTestBootstrap.initialize(); }
    private double previousFeRatio;
    private double previousAeRatio;
    @BeforeEach void setKnownPowerRatio() {
        previousFeRatio = appeng.api.config.PowerUnits.FE.conversionRatio;
        previousAeRatio = appeng.api.config.PowerUnits.AE.conversionRatio;
        appeng.api.config.PowerUnits.FE.conversionRatio = 0.5;
        appeng.api.config.PowerUnits.AE.conversionRatio = 1;
    }
    @AfterEach void restorePowerRatio() {
        appeng.api.config.PowerUnits.FE.conversionRatio = previousFeRatio;
        appeng.api.config.PowerUnits.AE.conversionRatio = previousAeRatio;
    }

    @Test void actualAdvancedAeRecipeRetainsAmountsFluidAndEnergy() {
        var source = new ReactionChamberRecipe(ID, new GenericStack(AEItemKey.of(Items.DIAMOND), 512),
            List.of(new IngredientStack.Item(Ingredient.of(Items.IRON_INGOT), 2048)),
            new IngredientStack.Fluid(Fluids.WATER, 500), 500000);
        var result = LargeWorkstationRecipeAdapters.adapt(source, "advanced_ae:reaction");
        assertEquals(2048, result.display().inputItems().get(0).count());
        assertEquals(500, result.display().inputFluid().amount());
        assertEquals(512, result.display().itemOutput().getCount());
        assertEquals(500000, result.energy());
        assertTrue(LargeWorkstationModule.ADVANCED_AE.unlocks(result.sourceType()));
    }

    @Test void actualOverloadRecipeRetainsStrictFluidNbtAndLightningCost() {
        var input = new FluidStack(Fluids.WATER, 1000);
        input.setTag(new CompoundTag());
        input.getTag().putInt("grade", 7);
        var source = new OverloadProcessingRecipe(ID, 0,
            List.of(new OverloadProcessingIngredient(Ingredient.of(Items.IRON_INGOT), 128)), input,
            List.of(new ItemStack(Items.DIAMOND, 1024)), new FluidStack(Fluids.LAVA, 250), 101,
            4, LightningKey.Tier.EXTREME_HIGH_VOLTAGE);
        var result = LargeWorkstationRecipeAdapters.adapt(source, "ae2lt:overload_processing");
        assertEquals(128, result.display().inputItems().get(0).count());
        assertEquals(1024, result.display().itemOutput().getCount());
        assertEquals(250, result.display().fluidOutput().getAmount());
        assertTrue(result.display().inputFluid().test(input));
        assertFalse(result.display().inputFluid().test(new FluidStack(Fluids.WATER, 1000)));
        assertEquals(51, result.energy());
        assertEquals(4, result.extraInputs().get(0).amount());
        assertEquals(LightningKey.of(LightningKey.Tier.EXTREME_HIGH_VOLTAGE), result.extraInputs().get(0).what());
    }

    @Test void actualAssemblyAndSimulationUseTheSameModuleWhileKeepingTheirTypes() {
        var inputs = List.of(new LightningSimulationIngredient(Ingredient.of(Items.IRON_INGOT), 32));
        var assembly = new LightningAssemblyRecipe(ID, 0, inputs, new ItemStack(Items.DIAMOND, 4),
            100, 4, LightningKey.Tier.HIGH_VOLTAGE);
        var simulation = new LightningSimulationRecipe(ID, 0, inputs, new ItemStack(Items.DIAMOND, 4),
            100, 4, LightningKey.Tier.HIGH_VOLTAGE);
        for (var source : List.of(assembly, simulation)) {
            String type = source == assembly ? "ae2lt:lightning_assembly" : "ae2lt:lightning_simulation";
            var adapted = LargeWorkstationRecipeAdapters.adapt(source, type);
            assertEquals(new ResourceLocation(type), adapted.sourceType());
            assertEquals(LargeWorkstationModule.LIGHTNING_TECH, LargeWorkstationModule.fromSourceType(adapted.sourceType()));
            assertEquals(32, adapted.display().inputItems().get(0).count());
            assertEquals(50, adapted.energy());
        }
    }

    @Test void configuredPowerRatioIsAppliedWithoutRoundingLargeIntegerEnergy() {
        appeng.api.config.PowerUnits.FE.conversionRatio = 1.25;
        var source = new LightningAssemblyRecipe(ID, 0,
            List.of(new LightningSimulationIngredient(Ingredient.of(Items.IRON_INGOT), 1)), new ItemStack(Items.DIAMOND),
            9_007_199_254_740_993L, 4, LightningKey.Tier.HIGH_VOLTAGE);
        assertEquals(11_258_999_068_426_242L, LargeWorkstationRecipeAdapters.adapt(source, "ae2lt:lightning_assembly").energy());
    }
}
