package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.compat.crafting.FluidIngredient;
import cn.dancingsnow.neoecoae.compat.crafting.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.compat.crafting.SizedIngredient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public record IntegratedWorkingStationRecipe(
        ResourceLocation id,
        List<SizedIngredient> inputItems,
        SizedFluidIngredient inputFluid,
        ItemStack itemOutput,
        FluidStack fluidOutput,
        int energy)
        implements Recipe<IntegratedWorkingStationRecipe.Input> {

    public static IntegratedWorkingStationRecipeBuilder builder() {
        return new IntegratedWorkingStationRecipeBuilder();
    }

    @Override
    public boolean matches(Input recipeInput, Level level) {
        List<ItemStack> provided = recipeInput.inputs();
        int[] remaining = new int[provided.size()];
        for (int i = 0; i < provided.size(); i++) {
            ItemStack stack = provided.get(i);
            remaining[i] = stack == null ? 0 : stack.getCount();
        }

        for (SizedIngredient req : inputItems) {
            int needed = req.count();
            for (int i = 0; i < provided.size() && needed > 0; i++) {
                ItemStack stack = provided.get(i);
                if (stack != null
                        && !stack.isEmpty()
                        && remaining[i] > 0
                        && req.ingredient().test(stack)) {
                    int take = Math.min(remaining[i], needed);
                    remaining[i] -= take;
                    needed -= take;
                }
            }
            if (needed > 0) {
                return false;
            }
        }

        FluidStack providedFluid = recipeInput.fluid();
        if (inputFluid.ingredient().isEmpty()) {
            return true;
        }
        if (providedFluid == null || providedFluid.isEmpty()) {
            return false;
        }
        if (!inputFluid.test(providedFluid)) {
            return false;
        }
        return providedFluid.getAmount() >= inputFluid.amount();
    }

    @Override
    public ItemStack assemble(Input inv, RegistryAccess registries) {
        return getResultItem(registries).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return itemOutput;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NERecipeTypes.INTEGRATED_WORKING_STATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NERecipeTypes.INTEGRATED_WORKING_STATION.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public boolean hasItemOutput() {
        return !itemOutput.isEmpty();
    }

    public boolean hasFluidOutput() {
        return !fluidOutput.isEmpty();
    }

    public static class Serializer implements RecipeSerializer<IntegratedWorkingStationRecipe> {
        @Override
        public IntegratedWorkingStationRecipe fromJson(ResourceLocation id, JsonObject json) {
            List<SizedIngredient> inputItems = new ArrayList<>();
            if (json.has("inputItems")) {
                JsonArray array = json.getAsJsonArray("inputItems");
                for (int i = 0; i < array.size(); i++) {
                    try {
                        inputItems.add(SizedIngredient.fromJson(array.get(i)));
                    } catch (JsonParseException e) {
                        throw new JsonParseException("Recipe " + id + " inputItems[" + i + "] " + e.getMessage(), e);
                    }
                }
            }

            SizedFluidIngredient inputFluid;
            try {
                inputFluid = json.has("inputFluid")
                        ? SizedFluidIngredient.fromJson(json.get("inputFluid"))
                        : new SizedFluidIngredient(FluidIngredient.empty(), 0);
            } catch (JsonParseException e) {
                throw new JsonParseException("Recipe " + id + " inputFluid " + e.getMessage(), e);
            }

            ItemStack itemOutput =
                    json.has("itemOutput") ? readItemStack(id, json.getAsJsonObject("itemOutput")) : ItemStack.EMPTY;
            FluidStack fluidOutput = json.has("fluidOutput")
                    ? readFluidStack(id, json.getAsJsonObject("fluidOutput"))
                    : FluidStack.EMPTY;
            int energy = json.has("energy") ? json.get("energy").getAsInt() : 0;
            return new IntegratedWorkingStationRecipe(id, inputItems, inputFluid, itemOutput, fluidOutput, energy);
        }

        private static ItemStack readItemStack(ResourceLocation recipeId, JsonObject object) {
            String field = object.has("item") ? "item" : object.has("id") ? "id" : null;
            if (field == null) {
                throw new JsonParseException("Recipe " + recipeId + " itemOutput must contain 'item' or 'id'");
            }
            ResourceLocation itemId = ResourceLocation.parse(object.get(field).getAsString());
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null) {
                throw new JsonParseException("Recipe " + recipeId + " has unknown item output '" + itemId + "'");
            }
            int count = object.has("count") ? object.get("count").getAsInt() : 1;
            return new ItemStack(item, count);
        }

        private static FluidStack readFluidStack(ResourceLocation recipeId, JsonObject object) {
            if (object.size() == 0) {
                return FluidStack.EMPTY;
            }
            if (object.has("tag")) {
                throw new JsonParseException("Recipe " + recipeId + " fluidOutput cannot use a tag");
            }
            String field = object.has("fluid") ? "fluid" : object.has("id") ? "id" : null;
            if (field == null) {
                throw new JsonParseException("Recipe " + recipeId + " fluidOutput must contain 'fluid' or 'id'");
            }
            ResourceLocation fluidId = ResourceLocation.parse(object.get(field).getAsString());
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
            if (fluid == null || fluid == Fluids.EMPTY) {
                throw new JsonParseException("Recipe " + recipeId + " has unknown fluid output '" + fluidId + "'");
            }
            int amount = object.has("amount") ? object.get("amount").getAsInt() : 1000;
            return new FluidStack(fluid, amount);
        }

        @Override
        public IntegratedWorkingStationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<SizedIngredient> inputItems = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                inputItems.add(SizedIngredient.fromNetwork(buffer));
            }
            return new IntegratedWorkingStationRecipe(
                    id,
                    inputItems,
                    SizedFluidIngredient.fromNetwork(buffer),
                    buffer.readItem(),
                    FluidStack.readFromPacket(buffer),
                    buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, IntegratedWorkingStationRecipe recipe) {
            buffer.writeVarInt(recipe.inputItems.size());
            for (SizedIngredient inputItem : recipe.inputItems) {
                inputItem.toNetwork(buffer);
            }
            recipe.inputFluid.toNetwork(buffer);
            buffer.writeItem(recipe.itemOutput);
            recipe.fluidOutput.writeToPacket(buffer);
            buffer.writeVarInt(recipe.energy);
        }
    }

    public static class Input extends SimpleContainer {
        private final List<ItemStack> inputs;

        @Nullable private final FluidStack fluid;

        public Input(List<ItemStack> inputs, @Nullable FluidStack fluid) {
            super(inputs.toArray(ItemStack[]::new));
            this.inputs = inputs;
            this.fluid = fluid;
        }

        public List<ItemStack> inputs() {
            return inputs;
        }

        @Nullable public FluidStack fluid() {
            return fluid;
        }
    }
}
