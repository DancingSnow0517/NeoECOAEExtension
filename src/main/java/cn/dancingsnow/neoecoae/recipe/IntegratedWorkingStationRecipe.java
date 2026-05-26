package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import cn.dancingsnow.neoecoae.compat.crafting.SizedIngredient;
import net.minecraftforge.fluids.FluidStack;
import cn.dancingsnow.neoecoae.compat.crafting.FluidIngredient;
import cn.dancingsnow.neoecoae.compat.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record IntegratedWorkingStationRecipe(
    ResourceLocation id,
    List<SizedIngredient> inputItems,
    SizedFluidIngredient inputFluid,
    ItemStack itemOutput,
    FluidStack fluidOutput,
    int energy
) implements Recipe<IntegratedWorkingStationRecipe.Input> {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public Builder require(Object ingredient) { return this; }
        public Builder require(Object ingredient, int count) { return this; }
        public Builder requireFluid(Object fluid, int amount) { return this; }
        public Builder itemOutput(Object output) { return this; }
        public Builder itemOutput(Object output, int count) { return this; }
        public Builder fluidOutput(Object output, int amount) { return this; }
        public Builder energy(int energy) { return this; }
        public void save(Object provider) {}
        public void save(Object provider, ResourceLocation id) {}
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
                if (stack != null && !stack.isEmpty() && remaining[i] > 0 && req.ingredient().test(stack)) {
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
        return providedFluid != null && !providedFluid.isEmpty() && inputFluid.test(providedFluid);
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
                    inputItems.add(SizedIngredient.fromJson(array.get(i)));
                }
            }
            SizedFluidIngredient inputFluid = json.has("inputFluid")
                ? SizedFluidIngredient.fromJson(json.get("inputFluid"))
                : new SizedFluidIngredient(FluidIngredient.empty(), 0);
            ItemStack itemOutput = json.has("itemOutput")
                ? ShapedRecipe.itemStackFromJson(json.getAsJsonObject("itemOutput"))
                : ItemStack.EMPTY;
            FluidStack fluidOutput = json.has("fluidOutput")
                ? new FluidStack(
                    SizedFluidIngredient.fromJson(json.get("fluidOutput")).ingredient().fluid(),
                    SizedFluidIngredient.fromJson(json.get("fluidOutput")).amount()
                )
                : FluidStack.EMPTY;
            int energy = json.has("energy") ? json.get("energy").getAsInt() : 0;
            return new IntegratedWorkingStationRecipe(id, inputItems, inputFluid, itemOutput, fluidOutput, energy);
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
                buffer.readVarInt()
            );
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
        @Nullable
        private final FluidStack fluid;

        public Input(List<ItemStack> inputs, @Nullable FluidStack fluid) {
            super(inputs.toArray(ItemStack[]::new));
            this.inputs = inputs;
            this.fluid = fluid;
        }

        public List<ItemStack> inputs() {
            return inputs;
        }

        @Nullable
        public FluidStack fluid() {
            return fluid;
        }
    }
}
