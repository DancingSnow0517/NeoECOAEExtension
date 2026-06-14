package cn.dancingsnow.neoecoae.data.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;

public final class CompatStorageMatrixRecipes {
    private CompatStorageMatrixRecipes() {}

    public static void init(RegistrateRecipeProvider provider) {
        saveShapedHousing(provider, "eco_fe_cell_housing", "appflux", "item", "appflux:harden_insulating_resin");
        saveManaHousing(provider);
        saveSourceHousing(provider);

        saveCells(provider, "appflux", "fe");
        saveCells(provider, "appbot", "mana");
        saveCells(provider, "arseng", "source");
    }

    private static void saveShapedHousing(
            RegistrateRecipeProvider provider, String id, String modid, String cType, String cValue) {
        provider.accept(new JsonRecipe(NeoECOAE.id(id), new ModLoadedCondition(modid)) {
            @Override
            protected void write(JsonObject json) {
                json.addProperty("type", "minecraft:crafting_shaped");
                json.addProperty("category", "misc");
                JsonArray pattern = new JsonArray();
                pattern.add("ABA");
                pattern.add("B B");
                pattern.add("CCC");
                json.add("pattern", pattern);

                JsonObject key = new JsonObject();
                key.add("A", item("neoecoae:crystal_matrix"));
                key.add("B", tag("forge:dusts/redstone"));
                key.add("C", ingredient(cType, cValue));
                json.add("key", key);

                json.add("result", result("neoecoae:" + id));
                json.addProperty("show_notification", true);
            }
        });
    }

    private static void saveManaHousing(RegistrateRecipeProvider provider) {
        provider.accept(
                new JsonRecipe(NeoECOAE.id("mana_infusion/eco_mana_cell_housing"), new ModLoadedCondition("appbot")) {
                    @Override
                    protected void write(JsonObject json) {
                        json.addProperty("type", "botania:mana_infusion");
                        json.add("input", item("neoecoae:eco_item_cell_housing"));
                        json.addProperty("mana", 100000);
                        json.add("output", result("neoecoae:eco_mana_cell_housing"));
                    }
                });
    }

    private static void saveSourceHousing(RegistrateRecipeProvider provider) {
        provider.accept(
                new JsonRecipe(
                        NeoECOAE.id("enchanting_apparatus/eco_source_cell_housing"), new ModLoadedCondition("arseng")) {
                    @Override
                    protected void write(JsonObject json) {
                        json.addProperty("type", "ars_nouveau:enchanting_apparatus");
                        json.addProperty("keepNbtOfReagent", false);
                        json.add("output", result("neoecoae:eco_source_cell_housing"));

                        JsonArray pedestalItems = new JsonArray();
                        pedestalItems.add(tag("forge:gems/source"));
                        pedestalItems.add(tag("forge:gems/source"));
                        pedestalItems.add(tag("forge:gems/source"));
                        pedestalItems.add(tag("forge:gems/source"));
                        pedestalItems.add(item("neoecoae:crystal_matrix"));
                        pedestalItems.add(item("neoecoae:crystal_matrix"));
                        pedestalItems.add(item("ars_nouveau:manipulation_essence"));
                        pedestalItems.add(item("ars_nouveau:manipulation_essence"));
                        json.add("pedestalItems", pedestalItems);

                        JsonArray reagent = new JsonArray();
                        reagent.add(item("neoecoae:eco_item_cell_housing"));
                        json.add("reagent", reagent);
                        json.addProperty("sourceCost", 5000);
                    }
                });
    }

    private static void saveCells(RegistrateRecipeProvider provider, String modid, String type) {
        saveCell(provider, modid, type, "16m");
        saveCell(provider, modid, type, "64m");
        saveCell(provider, modid, type, "256m");
    }

    private static void saveCell(RegistrateRecipeProvider provider, String modid, String type, String size) {
        provider.accept(
                new JsonRecipe(NeoECOAE.id("eco_" + type + "_storage_cell_" + size), new ModLoadedCondition(modid)) {
                    @Override
                    protected void write(JsonObject json) {
                        json.addProperty("type", "minecraft:crafting_shapeless");
                        json.addProperty("category", "misc");
                        JsonArray ingredients = new JsonArray();
                        ingredients.add(item("neoecoae:eco_" + type + "_cell_housing"));
                        ingredients.add(item("neoecoae:eco_cell_component_" + size));
                        json.add("ingredients", ingredients);
                        json.add("result", result("neoecoae:eco_" + type + "_storage_cell_" + size));
                    }
                });
    }

    private static JsonObject ingredient(String type, String value) {
        return switch (type) {
            case "item" -> item(value);
            case "tag" -> tag(value);
            default -> throw new IllegalArgumentException("Unknown ingredient type " + type);
        };
    }

    private static JsonObject item(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("item", id);
        return json;
    }

    private static JsonObject tag(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("tag", id);
        return json;
    }

    private static JsonObject result(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("item", id);
        return json;
    }

    private abstract static class JsonRecipe implements FinishedRecipe {
        private final ResourceLocation id;
        private final ICondition condition;

        JsonRecipe(ResourceLocation id, ICondition condition) {
            this.id = id;
            this.condition = condition;
        }

        @Override
        public void serializeRecipeData(JsonObject json) {
            write(json);
        }

        @Override
        public JsonObject serializeRecipe() {
            JsonObject json = new JsonObject();
            JsonArray conditions = new JsonArray();
            conditions.add(CraftingHelper.serialize(condition));
            json.add("conditions", conditions);
            serializeRecipeData(json);
            return json;
        }

        protected abstract void write(JsonObject json);

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
}
