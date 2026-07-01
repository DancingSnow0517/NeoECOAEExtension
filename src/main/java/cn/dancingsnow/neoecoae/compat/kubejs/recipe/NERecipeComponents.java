package cn.dancingsnow.neoecoae.compat.kubejs.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.item.InputItem;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.component.ComponentRole;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;

interface NERecipeComponents {
    RecipeComponent<InputItem> SIZED_INPUT_ITEM = new RecipeComponent<>() {
        @Override
        public ComponentRole role() {
            return ComponentRole.INPUT;
        }

        @Override
        public String componentType() {
            return "neoecoae_sized_input_item";
        }

        @Override
        public Class<?> componentClass() {
            return InputItem.class;
        }

        @Override
        public JsonElement write(RecipeJS recipe, InputItem value) {
            JsonObject json = new JsonObject();
            json.add("ingredient", recipe.writeInputItem(value));
            json.addProperty("count", value.count);
            return json;
        }

        @Override
        public InputItem read(RecipeJS recipe, Object from) {
            return recipe.readInputItem(from);
        }

        @Override
        public boolean hasPriority(RecipeJS recipe, Object from) {
            return recipe.inputItemHasPriority(from);
        }

        @Override
        public String checkEmpty(dev.latvian.mods.kubejs.recipe.RecipeKey<InputItem> key, InputItem value) {
            return value.isEmpty() ? key.name + " cannot be empty!" : "";
        }
    };
}
