package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public record CoolingRecipe(SizedFluidIngredient input, @Nullable FluidStackTemplate outputTemplate, int coolant, int maxOverclock) implements Recipe<CoolingRecipe.Input> {

    @Contract("-> new")
    public static CoolingRecipeBuilder builder() {
        return new CoolingRecipeBuilder();
    }

    public static CoolingRecipeBuilder builder(HolderGetter<Fluid> fluids) {
        return new CoolingRecipeBuilder(fluids);
    }

    @Override
    public boolean matches(Input i, Level l) {
        FluidStack output = output();
        return input.test(i.input) && (i.output.isEmpty() || output.is(i.output.getFluid()));
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public ItemStack assemble(Input input) {
        return ItemStack.EMPTY;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of();
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<? extends Recipe<Input>> getSerializer() {
        return NERecipeTypes.COOLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<Input>> getType() {
        return NERecipeTypes.COOLING.get();
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    public int inputAmount() {
        return input.amount();
    }

    public int outputAmount() {
        return output().getAmount();
    }

    public FluidStack output() {
        return outputTemplate == null ? FluidStack.EMPTY : outputTemplate.create();
    }

    public Optional<FluidStackTemplate> optionalOutputTemplate() {
        return Optional.ofNullable(outputTemplate);
    }

    private static final MapCodec<CoolingRecipe> CODEC = RecordCodecBuilder.mapCodec(ins -> ins.group(
        SizedFluidIngredient.CODEC.fieldOf("input").forGetter(CoolingRecipe::input),
        FluidStackTemplate.CODEC.optionalFieldOf("output").forGetter(CoolingRecipe::optionalOutputTemplate),
        Codec.INT.fieldOf("coolant").forGetter(CoolingRecipe::coolant),
        Codec.INT.optionalFieldOf("max_overclock", 0).forGetter(CoolingRecipe::maxOverclock)
    ).apply(ins, (input, output, coolant, maxOverclock) ->
        new CoolingRecipe(input, output.orElse(null), coolant, maxOverclock)
    ));

    private static final StreamCodec<RegistryFriendlyByteBuf, CoolingRecipe> STREAM_CODEC = StreamCodec.composite(
        SizedFluidIngredient.STREAM_CODEC,
        CoolingRecipe::input,
        ByteBufCodecs.optional(FluidStackTemplate.STREAM_CODEC),
        CoolingRecipe::optionalOutputTemplate,
        ByteBufCodecs.VAR_INT,
        CoolingRecipe::coolant,
        ByteBufCodecs.VAR_INT,
        CoolingRecipe::maxOverclock,
        (input, output, coolant, maxOverclock) ->
            new CoolingRecipe(input, output.orElse(null), coolant, maxOverclock)
    );

    public static final RecipeSerializer<CoolingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

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
