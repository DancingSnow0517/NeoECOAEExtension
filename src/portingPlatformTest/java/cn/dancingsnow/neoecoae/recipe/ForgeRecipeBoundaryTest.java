package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import cn.dancingsnow.neoecoae.platform.recipe.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.common.crafting.VanillaIngredientSerializer;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeRecipeBoundaryTest {
    private static final ResourceLocation ID = new ResourceLocation("neoecoae", "boundary_test");

    @BeforeAll static void bootstrap() {
        PlatformTestBootstrap.initialize();
        if (CraftingHelper.getID(VanillaIngredientSerializer.INSTANCE) == null)
            CraftingHelper.register(new ResourceLocation("minecraft", "item"), VanillaIngredientSerializer.INSTANCE);
        if (CraftingHelper.getID(StrictNBTIngredient.Serializer.INSTANCE) == null)
            CraftingHelper.register(new ResourceLocation("forge", "nbt"), StrictNBTIngredient.Serializer.INSTANCE);
    }

    private static JsonObject json(int inputs) {
        JsonObject json = JsonParser.parseString("""
            {"itemOutput":{"item":"minecraft:diamond","count":1024,"nbt":"{quality:7}"},"energy":2000}
            """).getAsJsonObject();
        JsonArray array = new JsonArray();
        for (int i = 0; i < inputs; i++) array.add(JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\",\"count\":4}"));
        json.add("inputItems", array);
        return json;
    }

    @Test void jsonRetainsCountNbtAndRecipeIdentity() {
        var serializer = new IntegratedWorkingStationRecipe.Serializer();
        var recipe = serializer.fromJson(ID, json(1));
        assertEquals(ID, recipe.getId());
        assertEquals(1024, recipe.itemOutput().getCount());
        assertEquals(7, recipe.itemOutput().getTag().getInt("quality"));
        var restored = serializer.fromJson(ID, IntegratedWorkingStationRecipe.Serializer.toJson(recipe));
        assertTrue(ItemStack.isSameItemSameTags(recipe.itemOutput(), restored.itemOutput()));
        assertEquals(recipe.inputItems().get(0).count(), restored.inputItems().get(0).count());
    }

    @Test void networkRetainsCountsBeyondTheVanillaByteRange() {
        var serializer = new IntegratedWorkingStationRecipe.Serializer();
        var recipe = serializer.fromJson(ID, json(1));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, recipe);
            var restored = serializer.fromNetwork(ID, buffer);
            assertEquals(1024, restored.itemOutput().getCount());
            assertEquals(7, restored.itemOutput().getTag().getInt("quality"));
            assertEquals(ID, restored.getId());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void singleBlockAndLargeRecipesKeepSeparateInputLimits() {
        assertThrows(IllegalArgumentException.class, () -> new IntegratedWorkingStationRecipe.Serializer().fromJson(ID, json(10)));
        var serializer = new LargeIntegratedWorkingStationRecipe.Serializer();
        assertEquals(12, serializer.fromJson(ID, json(12)).display().inputItems().size());
        assertThrows(IllegalArgumentException.class, () -> serializer.fromJson(ID, json(13)));
    }

    @Test void malformedNetworkSizesAreRejectedBeforeAllocation() {
        for (int size : new int[]{-1, 10, Integer.MAX_VALUE}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeVarInt(size);
                assertThrows(IllegalArgumentException.class, () -> new IntegratedWorkingStationRecipe.Serializer().fromNetwork(ID, buffer));
            } finally { buffer.release(); }
        }
    }

    @Test void alternativesAndRequiredQuantitiesSurviveJson() {
        var sized = new SizedIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), Integer.MAX_VALUE);
        var restored = SizedIngredient.fromJson(sized.toJson());
        assertEquals(Integer.MAX_VALUE, restored.count());
        assertTrue(restored.ingredient().test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(restored.ingredient().test(new ItemStack(Items.GOLD_INGOT)));
        assertFalse(restored.ingredient().test(new ItemStack(Items.DIAMOND)));
    }

    @Test void exactFluidNbtCannotBeWidenedToTheSameFluid() {
        var stack = new FluidStack(Fluids.WATER, 100);
        stack.setTag(new CompoundTag());
        stack.getTag().putInt("grade", 7);
        var predicate = FluidIngredient.exact(stack);
        var restored = FluidIngredient.fromJson(predicate.toJson());
        assertTrue(restored.test(stack));
        assertFalse(restored.test(new FluidStack(Fluids.WATER, 100)));
        var wrong = stack.copy();
        wrong.getTag().putInt("grade", 8);
        assertFalse(restored.test(wrong));
        assertFalse(restored.test(new FluidStack(Fluids.LAVA, 100)));
    }

    @Test void exactUntaggedFluidRejectsTaggedFluid() {
        var plain = new FluidStack(Fluids.WATER, 100);
        var tagged = plain.copy();
        tagged.setTag(new CompoundTag());
        tagged.getTag().putInt("extra", 1);
        assertTrue(FluidIngredient.exact(plain).test(plain));
        assertFalse(FluidIngredient.exact(plain).test(tagged));
        assertTrue(FluidIngredient.of(Fluids.WATER).test(tagged));
    }

    @Test void tagAndAlternativePredicatesRetainTheirNetworkRepresentation() {
        var tag = new SizedFluidIngredient(FluidIngredient.of(FluidTags.WATER), 4000);
        var alternatives = FluidIngredient.of(Fluids.WATER, Fluids.LAVA);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            tag.write(buffer);
            assertEquals(tag.toJson(), SizedFluidIngredient.read(buffer).toJson());
            assertTrue(FluidIngredient.fromJson(alternatives.toJson()).test(new FluidStack(Fluids.LAVA, 1)));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void unknownPredicateAndInvalidQuantitiesAreRejected() {
        assertThrows(RuntimeException.class, () -> FluidIngredient.fromJson(JsonParser.parseString("{\"custom\":true}")));
        assertThrows(RuntimeException.class, () -> RecipeJson.item(JsonParser.parseString("{\"item\":\"missing:item\"}")));
        assertThrows(RuntimeException.class, () -> new SizedIngredient(Ingredient.of(Items.STONE), 0));
        assertThrows(RuntimeException.class, () -> SizedFluidIngredient.of(Fluids.WATER, -1));
        assertThrows(RuntimeException.class, () -> RecipeJson.item(JsonParser.parseString("{\"item\":\"minecraft:stone\",\"components\":{}}")));
    }

    @Test void coolingJsonAndNetworkKeepAmountByproductAndTier() {
        var recipe = new CoolingRecipe(SizedFluidIngredient.of(Fluids.WATER, 100), new FluidStack(Fluids.LAVA, 25), 1500, 2, ID);
        var serializer = new CoolingRecipe.Serializer();
        var restored = serializer.fromJson(ID, CoolingRecipe.Serializer.toJson(recipe));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, restored);
            var synced = serializer.fromNetwork(ID, buffer);
            assertEquals(100, synced.inputAmount());
            assertEquals(25, synced.outputAmount());
            assertEquals(1500, synced.coolant());
            assertEquals(2, synced.maxOverclock());
            assertTrue(synced.matches(new CoolingRecipe.Input(new FluidStack(Fluids.WATER, 100), FluidStack.EMPTY), null));
            assertFalse(synced.matches(new CoolingRecipe.Input(new FluidStack(Fluids.WATER, 99), FluidStack.EMPTY), null));
        } finally { buffer.release(); }
    }

    @Test void immutableRecipeSnapshotDoesNotConsumeTheInventory() {
        var stack = new ItemStack(Items.IRON_INGOT, 4);
        var input = new IntegratedWorkingStationRecipe.Input(List.of(stack), FluidStack.EMPTY);
        var recipe = new IntegratedWorkingStationRecipe.Serializer().fromJson(ID, json(1));
        assertTrue(recipe.matches(input, null));
        assertEquals(4, stack.getCount());
        assertThrows(UnsupportedOperationException.class, () -> input.removeItem(0, 1));
        assertThrows(UnsupportedOperationException.class, input::clearContent);
    }

    @Test void repeatedRequirementsCannotSpendTheSameItemsTwice() {
        var recipe = new IntegratedWorkingStationRecipe.Serializer().fromJson(ID, json(2));
        assertFalse(recipe.matches(new IntegratedWorkingStationRecipe.Input(List.of(new ItemStack(Items.IRON_INGOT, 4)), FluidStack.EMPTY), null));
        assertTrue(recipe.matches(new IntegratedWorkingStationRecipe.Input(List.of(new ItemStack(Items.IRON_INGOT, 8)), FluidStack.EMPTY), null));
    }

    @Test void eaeRecipeHasAnExecutableContractAndRequiresItsModule() {
        var recipe = new ExtendedCrystalAssemblerRecipe.Serializer().fromJson(ID, json(3));
        var adapted = new LargeWorkstationRecipe(ID, recipe.display(), 2000, List.of(), ExtendedCrystalAssemblerRecipe.SOURCE_TYPE);
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.IRON_INGOT), 12);
        var outputs = new KeyCounter();
        outputs.add(AEItemKey.of(recipe.display().itemOutput()), 1024);
        assertTrue(adapted.matches(inputs, outputs));
        inputs.remove(AEItemKey.of(Items.IRON_INGOT), 1);
        assertFalse(adapted.matches(inputs, outputs));
        assertTrue(LargeWorkstationModule.EXTENDED_AE.unlocks(adapted.sourceType()));
        assertFalse(LargeWorkstationModule.REGULAR.unlocks(adapted.sourceType()));
        assertFalse(LargeWorkstationModule.ADVANCED_AE.unlocks(adapted.sourceType()));
        assertFalse(LargeWorkstationModule.LIGHTNING_TECH.unlocks(adapted.sourceType()));
    }

    @Test void catalogCachesFreshCollectionsButInvalidatesEqualReplacementObjects() {
        var manager = mock(RecipeManager.class);
        var first = new ExtendedCrystalAssemblerRecipe.Serializer().fromJson(ID, json(1));
        when(manager.getRecipes()).thenAnswer(ignored -> new java.util.ArrayList<>(List.of(first)));
        var before = LargeWorkstationRecipes.getAll(manager, RegistryAccess.EMPTY);
        assertSame(before, LargeWorkstationRecipes.getAll(manager, RegistryAccess.EMPTY));
        var replacement = new ExtendedCrystalAssemblerRecipe.Serializer().fromJson(ID, json(1));
        when(manager.getRecipes()).thenReturn(List.of(replacement));
        assertNotSame(before, LargeWorkstationRecipes.getAll(manager, RegistryAccess.EMPTY));
        assertSame(replacement.display(), LargeWorkstationRecipes.getById(manager, RegistryAccess.EMPTY, ID).display());
    }

    @Test void nbtPredicatesRemainDistinctDuringDeduplication() {
        var first = new ItemStack(Items.IRON_INGOT);
        first.setTag(new CompoundTag());
        first.getTag().putInt("grade", 1);
        var second = first.copy();
        second.getTag().putInt("grade", 2);
        var fluid = new SizedFluidIngredient(FluidIngredient.empty(), 1);
        var a = new LargeWorkstationRecipe(ID, new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(StrictNBTIngredient.of(first), 1)),
            fluid, new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 2000), 2000, List.of());
        var b = new LargeWorkstationRecipe(new ResourceLocation("neoecoae", "other"), new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(StrictNBTIngredient.of(second), 1)),
            fluid, new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 2000), 2000, List.of());
        assertEquals(2, LargeWorkstationRecipes.deduplicate(List.of(a, b), JsonOps.INSTANCE).size());
    }
}
