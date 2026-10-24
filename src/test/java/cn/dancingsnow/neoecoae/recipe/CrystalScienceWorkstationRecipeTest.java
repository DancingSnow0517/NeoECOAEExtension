package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.recipes.entropy.EntropyRecipe;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrystalScienceWorkstationRecipeTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void pulverizingPreservesItemCountsFluidsAndAeCost() {
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        var recipe = LargeWorkstationRecipes.decode(ResourceLocation.parse("test:pulverizer"), "ae2cs:crystal_pulverizer_recipe",
            JsonParser.parseString("""
                {"input":{"item":"minecraft:iron_ingot","count":4},"result":{"id":"minecraft:diamond","count":2},
                 "fluid_input":{"fluid":"minecraft:water","amount":250},
                 "fluid_output":{"id":"minecraft:lava","amount":125},"energy_cost":345}
                """).getAsJsonObject(), ops);
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.IRON_INGOT), 8);
        inputs.add(AEFluidKey.of(Fluids.WATER), 500);
        var outputs = new KeyCounter();
        outputs.add(AEItemKey.of(Items.DIAMOND), 4);
        outputs.add(AEFluidKey.of(Fluids.LAVA), 250);
        assertEquals(2, recipe.matchingMultiplier(inputs, outputs));
        assertEquals(345, recipe.energy());
        assertTrue(LargeWorkstationModule.CRYSTAL_SCIENCE.unlocks(recipe.sourceType()));
        assertFalse(LargeWorkstationModule.REGULAR.unlocks(recipe.sourceType()));
    }

    @Test void entropyPreservesBlockAndAllDropsRatherThanJustTheFirstOutput() {
        var source = entropy("""
            {"mode":"heat","input":{"block":{"id":"minecraft:stone"}},
             "output":{"block":{"id":"minecraft:cobblestone"},
               "drops":[{"id":"minecraft:diamond","count":2},{"id":"minecraft:emerald"}]}}
            """);
        var recipe = CrystalScienceWorkstationRecipes.entropy(ResourceLocation.parse("test:entropy"), source);
        assertNotNull(recipe);
        assertEquals(1600, recipe.energy());
        assertEquals(3, recipe.outputs().size());
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.STONE), 1);
        var outputs = new KeyCounter();
        for (var output : recipe.outputs()) outputs.add(output.what(), output.amount());
        assertTrue(recipe.matches(inputs, outputs));
        outputs.remove(AEItemKey.of(Items.EMERALD), 1);
        assertFalse(recipe.matches(inputs, outputs));
        assertTrue(LargeWorkstationModule.CRYSTAL_SCIENCE.unlocks(recipe.sourceType()));
        assertFalse(LargeWorkstationModule.REGULAR.unlocks(recipe.sourceType()));
    }

    @Test void flowingWaterSnowballsUseTheMachineFluidCostAndStoredSourceFluid() {
        var recipe = CrystalScienceWorkstationRecipes.entropy(ResourceLocation.parse("test:snowballs"), entropy("""
            {"mode":"cool","input":{"fluid":{"id":"minecraft:flowing_water"}},
             "output":{"drops":[{"id":"minecraft:snowball","count":4}]}}
            """));
        assertNotNull(recipe);
        var inputs = new KeyCounter();
        inputs.add(AEFluidKey.of(Fluids.WATER), 250);
        assertTrue(recipe.matchesInputs(inputs));
        inputs.remove(AEFluidKey.of(Fluids.WATER), 1);
        assertFalse(recipe.matchesInputs(inputs));
    }

    @Test void ae2AloneDoesNotUnlockEntropyRecipes() {
        var manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(ResourceLocation.parse("test:entropy_absent"), entropy("""
            {"mode":"heat","input":{"block":{"id":"minecraft:stone"}},"output":{"block":{"id":"minecraft:cobblestone"}}}
            """))));
        assertTrue(LargeWorkstationRecipes.getAll(manager,
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)).isEmpty());
    }

    private static EntropyRecipe entropy(String json) {
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        return EntropyRecipe.CODEC.codec().parse(ops, JsonParser.parseString(json)).getOrThrow();
    }
}
