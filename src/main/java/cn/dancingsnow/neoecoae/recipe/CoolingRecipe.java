package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Contract;

public record CoolingRecipe(SizedFluidIngredient input, FluidStack output, int coolant) implements Recipe<CoolingRecipe.Input> {

    @Contract("-> new")
    public static CoolingRecipeBuilder builder() {
        return new CoolingRecipeBuilder();
    }

    @Override
    public boolean matches(Input i, Level l) {
        return input.test(i.input) && (i.output.isEmpty() || output.is(i.output.getFluid()));
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NERecipeTypes.COOLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NERecipeTypes.COOLING.get();
    }

    public static class Serializer implements RecipeSerializer<CoolingRecipe> {
        private static final MapCodec<CoolingRecipe> CODEC = RecordCodecBuilder.mapCodec(ins -> ins.group(
            SizedFluidIngredient.NESTED_CODEC.fieldOf("input").forGetter(CoolingRecipe::input),
            FluidStack.OPTIONAL_CODEC.fieldOf("output").forGetter(CoolingRecipe::output),
            Codec.INT.fieldOf("coolant").forGetter(CoolingRecipe::coolant)
        ).apply(ins, CoolingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, CoolingRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedFluidIngredient.STREAM_CODEC,
            CoolingRecipe::input,
            FluidStack.OPTIONAL_STREAM_CODEC,
            CoolingRecipe::output,
            ByteBufCodecs.VAR_INT,
            CoolingRecipe::coolant,
            CoolingRecipe::new
        );

        @Override
        public MapCodec<CoolingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CoolingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

    public record Input(FluidStack input, FluidStack output) implements RecipeInput {

        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return input.isEmpty();
        }
    }
}
