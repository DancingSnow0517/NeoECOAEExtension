package cn.dancingsnow.neoecoae.datagen;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.registries.ForgeRegistries;

public final class EAERecipeData {
    private EAERecipeData() {}

    public static void register() {
        REGISTRATE.addDataGenerator(ProviderType.RECIPE, EAERecipeData::init);
    }

    public static void init(RegistrateRecipeProvider provider) {
        provider.accept(new CircuitCutterRecipe(
                NeoECOAE.id("circuit_cutter/superconducting_processor_print"),
                itemId(NEBlocks.ENERGIZED_SUPERCONDUCTIVE_BLOCK.get().asItem()),
                itemId(NEItems.SUPERCONDUCTING_PROCESSOR_PRINT.get()),
                9));
    }

    private record CircuitCutterRecipe(
            ResourceLocation id, ResourceLocation input, ResourceLocation output, int outputCount)
            implements FinishedRecipe {
        @Override
        public JsonObject serializeRecipe() {
            JsonObject json = new JsonObject();
            JsonArray conditions = new JsonArray();
            conditions.add(CraftingHelper.serialize(new ModLoadedCondition("expatternprovider")));
            json.add("conditions", conditions);
            json.addProperty("type", "expatternprovider:circuit_cutter");
            serializeRecipeData(json);
            return json;
        }

        @Override
        public void serializeRecipeData(JsonObject json) {
            JsonObject itemIngredient = new JsonObject();
            itemIngredient.addProperty("item", input.toString());
            JsonObject itemInput = new JsonObject();
            itemInput.add("ingredient", itemIngredient);
            itemInput.addProperty("amount", 1);
            json.add("item_input", itemInput);

            JsonObject fluidIngredient = new JsonObject();
            fluidIngredient.addProperty("fluid", "minecraft:water");
            JsonObject fluidInput = new JsonObject();
            fluidInput.add("ingredient", fluidIngredient);
            fluidInput.addProperty("amount", 100);
            json.add("fluid_input", fluidInput);

            JsonObject result = new JsonObject();
            result.addProperty("item", output.toString());
            result.addProperty("count", outputCount);
            json.add("output", result);
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
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }

    private static ResourceLocation itemId(net.minecraft.world.item.Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) {
            throw new IllegalStateException("Cannot serialize unregistered item " + item);
        }
        return id;
    }
}
