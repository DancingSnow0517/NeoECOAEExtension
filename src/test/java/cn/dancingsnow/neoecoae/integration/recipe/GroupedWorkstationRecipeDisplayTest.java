package cn.dancingsnow.neoecoae.integration.recipe;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipeGroup;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.network.chat.Component;
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
            var anyCard = assertInstanceOf(TranslatableContents.class, cards.getFirst().tooltip().get(1).getContents());
            assertEquals("gui.neoecoae.large_integrated_working_station.module_any", anyCard.getKey());
            assertEquals("First / Second", ((Component) anyCard.getArgs()[0]).getString());
            var firstCost = assertInstanceOf(TranslatableContents.class, cards.getFirst().tooltip().getLast().getContents());
            var secondCost = assertInstanceOf(TranslatableContents.class, cards.getLast().tooltip().getLast().getContents());
            assertEquals(12345L, firstCost.getArgs()[1]);
            assertEquals(67890L, secondCost.getArgs()[1]);
            assertTrue(((Component) firstCost.getArgs()[2]).getString().startsWith("4x"));
            assertTrue(((Component) secondCost.getArgs()[2]).getString().startsWith("32x"));
            var exactExtra = assertInstanceOf(TranslatableContents.class,
                cards.getLast().tooltip().getLast().getSiblings().getLast().getContents());
            assertEquals(123456L, exactExtra.getArgs()[0]);
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
