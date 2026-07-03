package cn.dancingsnow.neoecoae.data.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.datagen.EAERecipeData;
import com.google.gson.JsonObject;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.registries.ForgeRegistries;

public class EAERecipes {
    public static void init(RegistrateRecipeProvider provider) {
        EAERecipeData.init(provider);
        provider.accept(new ConditionalFinishedRecipe(
                new CrystalFixerRecipe(
                        NeoECOAE.id("crystal_fixer/damaged_budding_energized_crystal"),
                        blockId(NEBlocks.ENERGIZED_CRYSTAL_BLOCK.get()),
                        blockId(NEBlocks.DAMAGED_BUDDING_ENERGIZED_CRYSTAL.get()),
                        8000),
                new ModLoadedCondition("extendedae")));
        provider.accept(new ConditionalFinishedRecipe(
                new CrystalFixerRecipe(
                        NeoECOAE.id("crystal_fixer/chipped_budding_energized_crystal"),
                        blockId(NEBlocks.DAMAGED_BUDDING_ENERGIZED_CRYSTAL.get()),
                        blockId(NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get()),
                        8000),
                new ModLoadedCondition("extendedae")));
        provider.accept(new ConditionalFinishedRecipe(
                new CrystalFixerRecipe(
                        NeoECOAE.id("crystal_fixer/flawed_budding_energized_crystal"),
                        blockId(NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get()),
                        blockId(NEBlocks.FLAWED_BUDDING_ENERGIZED_CRYSTAL.get()),
                        500),
                new ModLoadedCondition("extendedae")));
        // ExtendedAE crystal assembler recipes are omitted in the 1.20.1 datagen path.
    }

    private static ResourceLocation blockId(net.minecraft.world.level.block.Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) {
            throw new IllegalStateException("Cannot serialize unregistered block " + block);
        }
        return id;
    }

    private record CrystalFixerRecipe(ResourceLocation id, ResourceLocation input, ResourceLocation output, int chance)
            implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("chance", chance);

            JsonObject fuelIngredient = new JsonObject();
            fuelIngredient.addProperty("tag", "forge:gems/energized_crystal");
            JsonObject fuel = new JsonObject();
            fuel.add("ingredient", fuelIngredient);
            json.add("fuel", fuel);

            JsonObject inputJson = new JsonObject();
            inputJson.addProperty("id", input.toString());
            inputJson.addProperty("count", 1);
            json.add("input", inputJson);

            JsonObject outputJson = new JsonObject();
            outputJson.addProperty("id", output.toString());
            outputJson.addProperty("count", 1);
            json.add("output", outputJson);
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return RecipeSerializer.SHAPELESS_RECIPE;
        }

        @Override
        public JsonObject serializeRecipe() {
            JsonObject json = FinishedRecipe.super.serializeRecipe();
            json.addProperty("type", "extendedae:crystal_fixer");
            return json;
        }

        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }
}
