package cn.dancingsnow.neoecoae.datagen;

import appeng.core.definitions.AEItems;
import appeng.datagen.providers.tags.ConventionTags;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class AAERecipeData {
    private AAERecipeData() {
    }

    public static void register() {
        REGISTRATE.addDataGenerator(ProviderType.RECIPE, AAERecipeData::init);
    }

    public static void init(RegistrateRecipeProvider provider) {
        ICondition advancedAeInstalled = new ModLoadedCondition("advanced_ae");

        provider.accept(new ReactionChamberRecipe(
            NeoECOAE.id("reaction_chamber/energized_crystal"),
            advancedAeInstalled,
            NEItems.ENERGIZED_CRYSTAL,
            64,
            500000,
            500,
            List.of(
                ItemInput.item(AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED, 32),
                ItemInput.tag(NETags.Items.ENERGIZED_CRYSTAL_DUST, 32)
            )
        ));

        provider.accept(new ReactionChamberRecipe(
            NeoECOAE.id("reaction_chamber/energized_fluix_crystal"),
            advancedAeInstalled,
            NEItems.ENERGIZED_FLUIX_CRYSTAL,
            64,
            500000,
            500,
            List.of(
                ItemInput.tag(NETags.Items.ENERGIZED_CRYSTAL_DUST, 64),
                ItemInput.tag(ConventionTags.FLUIX_CRYSTAL, 64)
            )
        ));
    }

    private record ReactionChamberRecipe(
        ResourceLocation id,
        ICondition condition,
        ItemLike output,
        long outputAmount,
        int energy,
        int waterAmount,
        List<ItemInput> inputs
    ) implements FinishedRecipe {
        @Override
        public JsonObject serializeRecipe() {
            JsonObject json = new JsonObject();
            JsonArray conditions = new JsonArray();
            conditions.add(CraftingHelper.serialize(condition));
            json.add("conditions", conditions);
            json.addProperty("type", "advanced_ae:reaction");
            serializeRecipeData(json);
            return json;
        }

        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("energy", energy);

            JsonObject fluidStack = new JsonObject();
            fluidStack.addProperty("Amount", waterAmount);
            fluidStack.addProperty("FluidName", "minecraft:water");
            JsonObject fluid = new JsonObject();
            fluid.add("fluidStack", fluidStack);
            json.add("fluid", fluid);

            JsonArray inputItems = new JsonArray();
            for (ItemInput input : inputs) {
                inputItems.add(input.toJson());
            }
            json.add("input_items", inputItems);

            JsonObject out = new JsonObject();
            out.addProperty("#", outputAmount);
            out.addProperty("#c", "ae2:i");
            out.addProperty("id", itemId(output).toString());
            json.add("output", out);
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

    private record ItemInput(int amount, JsonObject ingredient) {
        static ItemInput item(ItemLike item, int amount) {
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("item", itemId(item).toString());
            return new ItemInput(amount, ingredient);
        }

        static ItemInput tag(TagKey<Item> tag, int amount) {
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("tag", tag.location().toString());
            return new ItemInput(amount, ingredient);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("amount", amount);
            json.add("ingredient", ingredient);
            return json;
        }
    }

    private static ResourceLocation itemId(ItemLike item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.asItem());
        if (id == null) {
            throw new IllegalStateException("Cannot serialize unregistered item " + item);
        }
        return id;
    }
}
