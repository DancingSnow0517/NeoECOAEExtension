package cn.dancingsnow.neoecoae.integration.recipe;

import appeng.api.stacks.GenericStack;
import appeng.api.config.PowerUnits;
import appeng.api.config.PowerMultiplier;
import cn.dancingsnow.neoecoae.integration.appflux.WorkstationFluxEnergy;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipeGroup;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupedWorkstationRecipeDisplayTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void eachRouteDisplaysItsOwnCurrencyDurationBatchAndAdditionalResource() throws Exception {
        var display = new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 36)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), new ItemStack(Items.DIAMOND, 36), FluidStack.EMPTY, 57_600);
        var science = new LargeWorkstationRecipe(new ResourceLocation("test:science"), display, 57_600, List.of(),
            new ResourceLocation("ae2cs:circuit_etcher_recipe"));
        var lightning = new LargeWorkstationRecipe(new ResourceLocation("test:lightning"), display, 0,
            List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1)), new ResourceLocation("ae2lt:overload_processing"))
            .processing(4, 400_000, false);
        var first = variant(science, LargeWorkstationModule.CRYSTAL_SCIENCE);
        var second = variant(lightning, LargeWorkstationModule.LIGHTNING_TECH);
        var group = new LargeWorkstationRecipeGroup(science, List.of(first, second));
        assertEquals(2, group.upgradeCards().size());
        var scienceText = assertInstanceOf(TranslatableContents.class, LargeWorkstationRecipeDisplay.variantCost(first).getContents());
        var lightningText = assertInstanceOf(TranslatableContents.class, LargeWorkstationRecipeDisplay.variantCost(second).getContents());
        assertEquals("57,600 AE", ((Component) scienceText.getArgs()[1]).getString());
        assertEquals(200, assertInstanceOf(TranslatableContents.class, ((Component) scienceText.getArgs()[2]).getContents()).getArgs()[0]);
        assertEquals(4, assertInstanceOf(TranslatableContents.class, ((Component) lightningText.getArgs()[2]).getContents()).getArgs()[0]);
        assertTrue(((Component) lightningText.getArgs()[3]).getString().startsWith("36x"));
        try (var flux = mockStatic(WorkstationFluxEnergy.class)) {
            flux.when(WorkstationFluxEnergy::available).thenReturn(true);
            var tooltipMethod = LargeWorkstationRecipeDisplay.class.getDeclaredMethod("variantsTooltip", LargeWorkstationRecipe.class, List.class);
            tooltipMethod.setAccessible(true);
            @SuppressWarnings("unchecked")
            var lines = (List<Component>) tooltipMethod.invoke(null, science, group.variants());
            assertEquals(11, lines.size(), "Each route has its own lines and the FE notice appears once");
            assertEquals(LargeWorkstationModule.CRYSTAL_SCIENCE.color(), lines.get(0).getSiblings().get(0).getStyle().getColor().getValue());
            assertEquals(LargeWorkstationModule.LIGHTNING_TECH.color(), lines.get(4).getSiblings().get(0).getStyle().getColor().getValue());
            var cost = assertInstanceOf(TranslatableContents.class, lines.get(5).getSiblings().get(0).getContents());
            assertEquals("400,000 FE", ((Component) cost.getArgs()[0]).getString());
            assertTrue(lines.get(7).getString().contains("× 1"));
            assertTrue(lines.stream().noneMatch(line -> line.getString().contains("test:lightning")));
        }
    }

    @Test void feSummaryChoosesTheAvailableCurrencyAndPreservesUnitColors() {
        double oldAe = PowerUnits.AE.conversionRatio, oldFe = PowerUnits.FE.conversionRatio;
        double oldMultiplier = PowerMultiplier.CONFIG.multiplier;
        PowerUnits.AE.conversionRatio = 1;
        PowerUnits.FE.conversionRatio = 0.5;
        PowerMultiplier.CONFIG.multiplier = 1;
        try (var flux = mockStatic(WorkstationFluxEnergy.class)) {
            var display = new IntegratedWorkingStationRecipe(List.of(), new SizedFluidIngredient(FluidIngredient.empty(), 1),
                ItemStack.EMPTY, FluidStack.EMPTY, 0);
            var recipe = new LargeWorkstationRecipe(new ResourceLocation("test:fe"), display, 0, List.of()).processing(4, 1000, false);
            var ae = LargeWorkstationRecipeDisplay.summaryEnergy(recipe);
            assertEquals("500 AE", ae.getString());
            assertEquals(ChatFormatting.DARK_PURPLE.getColor(), ae.getSiblings().get(0).getStyle().getColor().getValue());
            assertEquals("0.5 AE", LargeWorkstationRecipeDisplay.summaryEnergy(recipe.processing(4, 1, false)).getString());
            flux.when(WorkstationFluxEnergy::available).thenReturn(true);
            var fe = LargeWorkstationRecipeDisplay.summaryEnergy(recipe);
            assertEquals("1K FE", fe.getString());
            assertEquals(ChatFormatting.DARK_GREEN.getColor(), fe.getSiblings().get(0).getStyle().getColor().getValue());
        } finally {
            PowerUnits.AE.conversionRatio = oldAe;
            PowerUnits.FE.conversionRatio = oldFe;
            PowerMultiplier.CONFIG.multiplier = oldMultiplier;
        }
    }

    private static LargeWorkstationRecipeGroup.Variant variant(LargeWorkstationRecipe recipe, LargeWorkstationModule module) {
        return new LargeWorkstationRecipeGroup.Variant(recipe, recipe.id(), recipe.sourceType(), Set.of(module), false, false);
    }
}
