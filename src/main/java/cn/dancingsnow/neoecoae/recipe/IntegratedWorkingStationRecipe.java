package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.platform.recipe.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public record IntegratedWorkingStationRecipe(List<SizedIngredient> inputItems, SizedFluidIngredient inputFluid,
        ItemStack itemOutput, FluidStack fluidOutput, int energy, ResourceLocation id, int processingTicks)
        implements Recipe<IntegratedWorkingStationRecipe.Input> {
    public IntegratedWorkingStationRecipe(List<SizedIngredient> items, SizedFluidIngredient fluid,
            ItemStack itemOutput, FluidStack fluidOutput, int energy) {
        this(items, fluid, itemOutput, fluidOutput, energy, new ResourceLocation("neoecoae", "runtime_display"));
    }

    public IntegratedWorkingStationRecipe(List<SizedIngredient> items, SizedFluidIngredient fluid,
            ItemStack itemOutput, FluidStack fluidOutput, int energy, ResourceLocation id) {
        this(items, fluid, itemOutput, fluidOutput, energy, id, 100);
    }

    public IntegratedWorkingStationRecipe {
        if (processingTicks <= 0) throw new IllegalArgumentException("Processing time must be positive");
        inputItems = List.copyOf(inputItems);
        itemOutput = itemOutput.copy();
        fluidOutput = fluidOutput.copy();
        if (energy < 0) throw new IllegalArgumentException("Negative recipe energy");
    }

    public static IntegratedWorkingStationRecipeBuilder builder() { return new IntegratedWorkingStationRecipeBuilder(); }

    @Override
    public boolean matches(Input input, Level level) {
        var provided = input.inputs();
        int[] remaining = provided.stream().mapToInt(stack -> stack == null ? 0 : stack.getCount()).toArray();
        for (var requirement : inputItems) {
            int needed = requirement.count();
            for (int slot = 0; slot < provided.size() && needed > 0; slot++) {
                var stack = provided.get(slot);
                if (stack == null || stack.isEmpty() || remaining[slot] <= 0) continue;
                if (requirement.ingredient().test(stack)) {
                    int taken = Math.min(remaining[slot], needed);
                    remaining[slot] -= taken;
                    needed -= taken;
                }
            }
            if (needed > 0) return false;
        }
        return inputFluid.ingredient().isEmpty() || inputFluid.test(input.fluid());
    }

    @Override public ItemStack assemble(Input input, RegistryAccess registries) { return itemOutput.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return itemOutput; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NERecipeTypes.INTEGRATED_WORKING_STATION_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NERecipeTypes.INTEGRATED_WORKING_STATION.get(); }
    @Override public boolean isSpecial() { return true; }
    public boolean hasItemOutput() { return !itemOutput.isEmpty(); }
    public boolean hasFluidOutput() { return !fluidOutput.isEmpty(); }

    public static class Serializer implements RecipeSerializer<IntegratedWorkingStationRecipe> {
        public static final int MAX_ITEM_INPUTS = 9;

        @Override
        public IntegratedWorkingStationRecipe fromJson(ResourceLocation id, JsonObject json) {
            return fromJson(id, json, MAX_ITEM_INPUTS);
        }

        public static IntegratedWorkingStationRecipe fromJson(ResourceLocation id, JsonObject json, int maxInputs) {
            var inputs = new ArrayList<SizedIngredient>();
            if (json.has("inputItems")) {
                JsonArray array = GsonHelper.getAsJsonArray(json, "inputItems");
                if (array.size() > maxInputs) throw new IllegalArgumentException("Too many item inputs: " + array.size());
                for (var element : array) inputs.add(SizedIngredient.fromJson(element));
            }
            var fluid = json.has("inputFluid") ? SizedFluidIngredient.fromJson(json.get("inputFluid"))
                : new SizedFluidIngredient(FluidIngredient.empty(), 1);
            var recipe = new IntegratedWorkingStationRecipe(inputs, fluid, RecipeJson.item(json.get("itemOutput")),
                RecipeJson.fluid(json.get("fluidOutput")), GsonHelper.getAsInt(json, "energy"), id, GsonHelper.getAsInt(json, "processingTicks", 100));
            validate(recipe, maxInputs);
            return recipe;
        }

        public static JsonObject toJson(IntegratedWorkingStationRecipe recipe) {
            JsonObject json = new JsonObject();
            JsonArray items = new JsonArray();
            for (var input : recipe.inputItems()) items.add(input.toJson());
            json.add("inputItems", items);
            if (!recipe.inputFluid().ingredient().isEmpty()) json.add("inputFluid", recipe.inputFluid().toJson());
            if (recipe.hasItemOutput()) json.add("itemOutput", RecipeJson.item(recipe.itemOutput()));
            if (recipe.hasFluidOutput()) json.add("fluidOutput", RecipeJson.fluid(recipe.fluidOutput()));
            json.addProperty("energy", recipe.energy());
            if (recipe.processingTicks() != 100) json.addProperty("processingTicks", recipe.processingTicks());
            return json;
        }

        @Override
        public IntegratedWorkingStationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return read(id, buffer, MAX_ITEM_INPUTS);
        }

        public static IntegratedWorkingStationRecipe read(ResourceLocation id, FriendlyByteBuf buffer, int maxInputs) {
            int size = buffer.readVarInt();
            if (size < 0 || size > maxInputs) throw new IllegalArgumentException("Invalid item input count: " + size);
            var inputs = new ArrayList<SizedIngredient>(size);
            for (int i = 0; i < size; i++) inputs.add(SizedIngredient.read(buffer));
            var fluid = SizedFluidIngredient.read(buffer);
            var recipe = new IntegratedWorkingStationRecipe(inputs, fluid, RecipeJson.readItem(buffer),
                buffer.readFluidStack(), buffer.readVarInt(), id, buffer.readVarInt());
            validate(recipe, maxInputs);
            return recipe;
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, IntegratedWorkingStationRecipe recipe) {
            write(buffer, recipe, MAX_ITEM_INPUTS);
        }

        public static void write(FriendlyByteBuf buffer, IntegratedWorkingStationRecipe recipe, int maxInputs) {
            validate(recipe, maxInputs);
            buffer.writeVarInt(recipe.inputItems().size());
            for (var input : recipe.inputItems()) input.write(buffer);
            recipe.inputFluid().write(buffer);
            RecipeJson.writeItem(buffer, recipe.itemOutput());
            buffer.writeFluidStack(recipe.fluidOutput());
            buffer.writeVarInt(recipe.energy());
            buffer.writeVarInt(recipe.processingTicks());
        }

        private static void validate(IntegratedWorkingStationRecipe recipe, int maxInputs) {
            if (recipe.inputItems().size() > maxInputs) throw new IllegalArgumentException("Too many item inputs");
            if (recipe.inputItems().isEmpty() && recipe.inputFluid().ingredient().isEmpty()) {
                throw new IllegalArgumentException("Recipe requires an item or fluid input");
            }
            if (!recipe.hasItemOutput() && !recipe.hasFluidOutput()) throw new IllegalArgumentException("Recipe requires an output");
        }
    }

    public record Input(List<ItemStack> inputs, @Nullable FluidStack fluid) implements NERecipeInput {
        @Override public ItemStack getItem(int index) { return inputs.get(index); }
        @Override public int size() { return inputs.size(); }
        @Override public boolean isEmpty() {
            return inputs.stream().allMatch(stack -> stack == null || stack.isEmpty()) && (fluid == null || fluid.isEmpty());
        }
    }
}
