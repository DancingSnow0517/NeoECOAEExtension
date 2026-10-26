package cn.dancingsnow.neoecoae.impl.storage.bulk;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ECOCompressionServiceTest {
    static AEItemKey a, b, c, d;
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize(); a = AEItemKey.of(Items.IRON_NUGGET); b = AEItemKey.of(Items.IRON_INGOT);
        c = AEItemKey.of(Items.IRON_BLOCK); d = AEItemKey.of(Items.GOLD_BLOCK);
    }
    static ECOCompressionService.Edge edge(AEItemKey from, AEItemKey to, int ratio) { return new ECOCompressionService.Edge(from, to, ratio); }
    @Test void ratiosComeFromEdgesAndRecipeOrderDoesNotChangeTheResult() {
        var edges = new ArrayList<>(List.of(edge(a, b, 4), edge(b, c, 9)));
        var result = ECOCompressionService.buildChains(edges);
        Collections.reverse(edges);
        assertEquals(result, ECOCompressionService.buildChains(edges));
        assertEquals(List.of(BigInteger.ONE, BigInteger.valueOf(4), BigInteger.valueOf(36)), result.getFirst().factors());
    }
    @Test void duplicatesDoNotCreateConflicts() {
        assertEquals(1, ECOCompressionService.buildChains(List.of(edge(a, b, 9), edge(a, b, 9))).size());
    }
    @Test void branchesCyclesAndRatioConflictsAreRejectedAsComponents() {
        assertTrue(ECOCompressionService.buildChains(List.of(edge(a, b, 9), edge(a, c, 4))).isEmpty());
        assertTrue(ECOCompressionService.buildChains(List.of(edge(a, b, 9), edge(b, c, 9), edge(c, a, 4))).isEmpty());
        assertTrue(ECOCompressionService.buildChains(List.of(edge(a, b, 9), edge(a, b, 4))).isEmpty());
        assertTrue(ECOCompressionService.buildChains(List.of(edge(a, c, 9), edge(b, c, 9))).isEmpty());
    }
    @Test void invalidComponentDoesNotSuppressIndependentChain() {
        var result = ECOCompressionService.buildChains(List.of(edge(a, b, 9), edge(b, a, 4), edge(c, d, 4)));
        assertEquals(1, result.size()); assertEquals(List.of(c, d), result.getFirst().items());
    }
    @Test void denominationTablesRejectMutationAndInvalidFactors() {
        var items = new ArrayList<>(List.of(a, b)); var factors = new ArrayList<>(List.of(BigInteger.ONE, BigInteger.valueOf(9)));
        var units = new BulkUnits(items, factors); items.clear(); factors.clear();
        assertEquals(2, units.items().size());
        assertThrows(UnsupportedOperationException.class, () -> units.items().clear());
        assertThrows(IllegalArgumentException.class, () -> new BulkUnits(List.of(a, b), List.of(BigInteger.ONE, BigInteger.ONE)));
    }
    @Test void factorsAndRemaindersCanExceedLongWithoutTruncation() {
        BigInteger factor = BigInteger.TEN.pow(30);
        var units = new BulkUnits(List.of(a, b), List.of(BigInteger.ONE, factor));
        var totals = new java.util.HashMap<AEItemKey, BigInteger>();
        units.visit(factor.multiply(BigInteger.valueOf(3)).add(BigInteger.TWO), 1, totals::put);
        assertEquals(java.util.Map.of(a, BigInteger.TWO, b, BigInteger.valueOf(3)), totals);
    }
    @Test void realCraftingRecipesRequireAnExactReverseAndUseNonemptySlots() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var pattern = ShapedRecipePattern.of(java.util.Map.of('#', Ingredient.of(Items.IRON_NUGGET)), List.of("###", "###", "###"));
        var compressed = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, new ItemStack(Items.IRON_INGOT));
        var decompressed = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.IRON_NUGGET, 9),
            NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT)));
        assertEquals(List.of(edge(a, b, 9)), ECOCompressionService.recognizeRecipes(List.of(compressed, decompressed), registries, null));
        assertTrue(ECOCompressionService.recognizeRecipes(List.of(compressed), registries, null).isEmpty());
        var lossy = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.IRON_NUGGET, 8),
            NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT)));
        assertTrue(ECOCompressionService.recognizeRecipes(List.of(compressed, lossy), registries, null).isEmpty());
        var padded = ShapedRecipePattern.of(java.util.Map.of('#', Ingredient.of(Items.IRON_NUGGET)), List.of("# #", "   ", "# #"));
        var four = new ShapedRecipe("", CraftingBookCategory.MISC, padded, new ItemStack(Items.IRON_INGOT));
        var unpackFour = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.IRON_NUGGET, 4),
            NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT)));
        assertEquals(List.of(edge(a, b, 4)), ECOCompressionService.recognizeRecipes(List.of(four, unpackFour), registries, null));
    }
    @Test void conversionPatternEncodingKeepsIdentityAndExactInputValidation() {
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Exact ingot"));
        var input = AEItemKey.of(named);
        var pattern = new ECOBulkConversionPattern(input, 9, c, 1);
        assertEquals(pattern, ECOBulkConversionPattern.decode(pattern.encode()));
        var holder = new appeng.api.stacks.KeyCounter(); holder.add(input, 9);
        assertTrue(pattern.matchesInputs(new appeng.api.stacks.KeyCounter[] {holder}));
        holder.add(b, 1); assertFalse(pattern.matchesInputs(new appeng.api.stacks.KeyCounter[] {holder}));
        assertFalse(pattern.supportsPushInputsToExternalInventory());
    }
}
