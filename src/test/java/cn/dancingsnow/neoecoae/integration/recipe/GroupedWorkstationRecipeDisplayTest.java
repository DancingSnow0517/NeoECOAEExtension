package cn.dancingsnow.neoecoae.integration.recipe;

import appeng.api.stacks.GenericStack;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.integration.appflux.WorkstationFluxEnergy;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipeGroup;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupedWorkstationRecipeDisplayTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void groupedCardsUseTheDynamicLayoutAndShowOnlyTheirOwnOriginalCostsAndBatchSizes() {
        var first = module("first", "minecraft:diamond", "First");
        var second = module("second", "minecraft:emerald", "Second");
        ECOWorkstationModules.register(0, first);
        ECOWorkstationModules.register(0, second);
        try {
            var small = recipe("small", 4, 12345, List.of());
            var large = recipe("large", 32, 67890, List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 123456)));
            var group = new LargeWorkstationRecipeGroup(small, List.of(variant(small, first.id()), variant(large, second.id())));
            var cards = LargeWorkstationRecipeDisplay.cards(small, group);
            assertEquals(2, cards.size());
            assertTrue(cards.getFirst().stack().is(Items.DIAMOND));
            assertTrue(cards.getLast().stack().is(Items.EMERALD));
            assertEquals(1, cards.getFirst().stack().getCount(), "Support cards are catalysts, not consumed ingredients");
            assertEquals("First", cards.getFirst().tooltip().getFirst().getString());
            assertEquals("Second", cards.getLast().tooltip().getFirst().getString());
            assertEquals(first.color(), cards.getFirst().tooltip().getFirst().getSiblings().getFirst().getStyle().getColor().getValue());
            var firstCost = assertInstanceOf(TranslatableContents.class, cards.getFirst().tooltip().get(1).getSiblings().getFirst().getContents());
            var secondCost = assertInstanceOf(TranslatableContents.class, cards.getLast().tooltip().get(1).getSiblings().getFirst().getContents());
            assertEquals("12,345 AE", ((Component) firstCost.getArgs()[0]).getString());
            assertEquals("67,890 AE", ((Component) secondCost.getArgs()[0]).getString());
            assertEquals(3, cards.getFirst().tooltip().size(), "The material preview already shows the representative batch");
            assertTrue(cards.getLast().tooltip().get(3).getString().contains("32"), "Different minimum batches remain explicit");
            assertTrue(cards.getLast().tooltip().getLast().getString().contains("123,456"));
            assertTrue(cards.stream().flatMap(card -> card.tooltip().stream()).noneMatch(line -> line.getString().contains("group_display_test:")));
            var layout = new WorkstationRecipeInfoLayout(cards.size());
            assertEquals(2, layout.cardColumns());
            assertTrue(layout.cardX(0) > layout.textWidth());
        } finally {
            ECOWorkstationModules.unregister(first.id());
            ECOWorkstationModules.unregister(second.id());
        }
    }

    @Test void missingGroupKeepsSingleSourceCardSupport() {
        var module = module("legacy", "minecraft:diamond", "Legacy");
        ECOWorkstationModules.register(0, module);
        try {
            var recipe = recipe("legacy", 4, 2000, List.of());
            var cards = LargeWorkstationRecipeDisplay.cards(recipe, null);
            assertEquals(1, cards.size());
            assertTrue(cards.getFirst().stack().is(Items.DIAMOND));
        } finally { ECOWorkstationModules.unregister(module.id()); }
    }

    @Test void oneCardAndBatchShowOnlyTheFixedPassiveLightningCost() {
        var module = module("lightning", "minecraft:diamond", "Lightning");
        ECOWorkstationModules.register(0, module);
        try (var flux = mockStatic(WorkstationFluxEnergy.class)) {
            flux.when(WorkstationFluxEnergy::available).thenReturn(true);
            var preferred = recipe("slow", 4, 0, List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 8)))
                .processing(200, 1_000_000, false);
            var alternate = recipe("fast", 4, 0, List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1)))
                .processing(5, 2_000_000, false);
            var group = new LargeWorkstationRecipeGroup(preferred,
                List.of(variant(alternate, module.id()), variant(preferred, module.id())));
            var cards = LargeWorkstationRecipeDisplay.cards(preferred, group);
            assertEquals(1, cards.size());
            var lines = cards.getFirst().tooltip();
            assertEquals(1, lines.stream().filter(line -> line.getString().equals("Lightning")).count());
            var cost = assertInstanceOf(TranslatableContents.class, lines.get(1).getSiblings().getFirst().getContents());
            assertEquals("1,000,000 FE", ((Component) cost.getArgs()[0]).getString());
            assertTrue(lines.stream().anyMatch(line -> line.getString().contains(" × 8")));
            assertFalse(lines.stream().anyMatch(line -> line.getString().contains(" × 1")));
        } finally { ECOWorkstationModules.unregister(module.id()); }
    }

    @Test void feSummaryChoosesTheAvailableUnitAndKeepsTheUnitColorAndFractionalAeFallback() {
        double oldAe = PowerUnit.AE.conversionRatio, oldFe = PowerUnit.FE.conversionRatio;
        double oldMultiplier = PowerMultiplier.CONFIG.multiplier;
        PowerUnit.AE.conversionRatio = 1;
        PowerUnit.FE.conversionRatio = 0.5;
        PowerMultiplier.CONFIG.multiplier = 1;
        try (var flux = mockStatic(WorkstationFluxEnergy.class)) {
            var recipe = recipe("fe", 4, 0, List.of()).processing(4, 1000, false);
            var ae = LargeWorkstationRecipeDisplay.summaryEnergy(recipe);
            assertEquals("500 AE", ae.getString());
            assertEquals(ChatFormatting.DARK_PURPLE.getColor(), ae.getSiblings().getLast().getStyle().getColor().getValue());
            assertEquals("0.5 AE", LargeWorkstationRecipeDisplay.summaryEnergy(recipe.processing(4, 1, false)).getString());
            flux.when(WorkstationFluxEnergy::available).thenReturn(true);
            var fe = LargeWorkstationRecipeDisplay.summaryEnergy(recipe);
            assertEquals("1K FE", fe.getString());
            assertEquals(ChatFormatting.DARK_GREEN.getColor(), fe.getSiblings().getLast().getStyle().getColor().getValue());
        } finally {
            PowerUnit.AE.conversionRatio = oldAe;
            PowerUnit.FE.conversionRatio = oldFe;
            PowerMultiplier.CONFIG.multiplier = oldMultiplier;
        }
    }

    private static ECOWorkstationModules.Module module(String path, String card, String title) {
        return new ECOWorkstationModules.Module(id(path), ResourceLocation.parse(card), Set.of(id(path)),
            Component.literal(title), Component.literal(title), 0, null, null, null, null);
    }

    private static LargeWorkstationRecipe recipe(String path, int count, long energy, List<GenericStack> extras) {
        var display = new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), count)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), new ItemStack(Items.GOLD_INGOT, count), FluidStack.EMPTY, (int) energy);
        return new LargeWorkstationRecipe(id(path), display, energy, extras, id(path));
    }

    private static LargeWorkstationRecipeGroup.Variant variant(LargeWorkstationRecipe recipe, ResourceLocation module) {
        return new LargeWorkstationRecipeGroup.Variant(recipe, recipe.id(), recipe.sourceType(), Set.of(module), false, false);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("group_display_test", path); }
}
