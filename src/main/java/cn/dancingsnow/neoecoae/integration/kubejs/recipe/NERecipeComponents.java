package cn.dancingsnow.neoecoae.integration.kubejs.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.fluid.EmptyFluidStackJS;
import dev.latvian.mods.kubejs.fluid.FluidLike;
import dev.latvian.mods.kubejs.fluid.FluidStackJS;
import dev.latvian.mods.kubejs.fluid.InputFluid;
import dev.latvian.mods.kubejs.fluid.OutputFluid;
import dev.latvian.mods.kubejs.item.InputItem;
import dev.latvian.mods.kubejs.item.OutputItem;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.component.ComponentRole;
import dev.latvian.mods.kubejs.recipe.component.FluidComponents;
import dev.latvian.mods.kubejs.recipe.component.ItemComponents;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import net.minecraft.resources.ResourceLocation;

interface NERecipeComponents {
    // Datagen writes KubeJS' canonical fields. These components only keep older
    // datapacks/scripts readable when they still use NeoECOAE's pre-fix JSON.
    RecipeComponent<InputFluid> ID_COMPAT_INPUT_FLUID = new RecipeComponent<>() {
        @Override
        public ComponentRole role() {
            return ComponentRole.INPUT;
        }

        @Override
        public String componentType() {
            return "neoecoae_input_fluid";
        }

        @Override
        public Class<?> componentClass() {
            return InputFluid.class;
        }

        @Override
        public JsonElement write(RecipeJS recipe, InputFluid value) {
            if (value instanceof TagInputFluid tagInput) {
                JsonObject json = new JsonObject();
                json.addProperty("tag", tagInput.tag().toString());
                json.addProperty("amount", tagInput.amount());
                return json;
            }
            return recipe.writeInputFluid(value);
        }

        @Override
        public InputFluid read(RecipeJS recipe, Object from) {
            if (from instanceof JsonObject object && object.has("tag")) {
                ResourceLocation tag = ResourceLocation.parse(object.get("tag").getAsString());
                long amount = object.has("amount") ? object.get("amount").getAsLong() : 1L;
                return new TagInputFluid(tag, amount);
            }
            return recipe.readInputFluid(normalizeIdField(from, "fluid"));
        }

        @Override
        public boolean hasPriority(RecipeJS recipe, Object from) {
            return from instanceof JsonObject object && object.has("tag")
                    || recipe.inputFluidHasPriority(normalizeIdField(from, "fluid"));
        }

        @Override
        public String checkEmpty(dev.latvian.mods.kubejs.recipe.RecipeKey<InputFluid> key, InputFluid value) {
            return value.kjs$isEmpty() ? key.name + " cannot be empty!" : "";
        }
    };

    RecipeComponent<OutputItem> ID_COMPAT_OUTPUT_ITEM =
            ItemComponents.OUTPUT.mapIn(from -> normalizeIdField(from, "item"));

    RecipeComponent<OutputFluid> ID_COMPAT_OUTPUT_FLUID = new RecipeComponent<>() {
        @Override
        public ComponentRole role() {
            return ComponentRole.OUTPUT;
        }

        @Override
        public String componentType() {
            return "neoecoae_output_fluid";
        }

        @Override
        public Class<?> componentClass() {
            return OutputFluid.class;
        }

        @Override
        public JsonElement write(RecipeJS recipe, OutputFluid value) {
            if (value.kjs$isEmpty()) {
                return new JsonObject();
            }
            return recipe.writeOutputFluid(value);
        }

        @Override
        public OutputFluid read(RecipeJS recipe, Object from) {
            if (from instanceof JsonObject object && object.size() == 0) {
                return EmptyFluidStackJS.INSTANCE;
            }
            return recipe.readOutputFluid(normalizeIdField(from, "fluid"));
        }

        @Override
        public boolean hasPriority(RecipeJS recipe, Object from) {
            return from instanceof JsonObject object && object.size() == 0
                    || recipe.outputFluidHasPriority(normalizeIdField(from, "fluid"));
        }

        @Override
        public boolean isOutput(RecipeJS recipe, OutputFluid value, dev.latvian.mods.kubejs.recipe.ReplacementMatch match) {
            return match instanceof FluidLike fluidLike && value.matches(fluidLike);
        }
    };

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

    private static Object normalizeIdField(Object from, String canonicalField) {
        if (!(from instanceof JsonObject object) || object.has(canonicalField) || !object.has("id")) {
            return from;
        }

        JsonObject normalized = object.deepCopy();
        normalized.add(canonicalField, normalized.get("id"));
        return normalized;
    }

    record TagInputFluid(ResourceLocation tag, long amount) implements InputFluid {
        @Override
        public long kjs$getAmount() {
            return amount;
        }

        @Override
        public boolean kjs$isEmpty() {
            return amount <= 0 || tag == null;
        }

        @Override
        public FluidLike kjs$copy(long amount) {
            return new TagInputFluid(tag, amount);
        }

        @Override
        public boolean matches(FluidLike other) {
            return other instanceof FluidStackJS stack && stack.hasTag(tag);
        }
    }
}
