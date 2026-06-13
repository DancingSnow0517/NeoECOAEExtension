package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
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
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public record IntegratedWorkingStationRecipe(
    List<SizedIngredient> inputItems,
    SizedFluidIngredient inputFluid,
    @Nullable ItemStackTemplate itemOutputTemplate,
    @Nullable FluidStackTemplate fluidOutputTemplate,
    int energy
) implements Recipe<IntegratedWorkingStationRecipe.Input> {

    public static IntegratedWorkingStationRecipeBuilder builder() {
        return new IntegratedWorkingStationRecipeBuilder();
    }

    public static IntegratedWorkingStationRecipeBuilder builder(HolderGetter<Item> items, HolderGetter<Fluid> fluids) {
        return new IntegratedWorkingStationRecipeBuilder(items, fluids);
    }

    @Override
    public boolean matches(Input recipeInput, Level level) {
        // Match item inputs with quantities: simulate consuming from provided stacks so the same slot isn't reused
        var provided = recipeInput.inputs();
        // mutable remaining counts for each provided slot
        int[] remaining = new int[provided.size()];
        for (int i = 0; i < provided.size(); i++) {
            var s = provided.get(i);
            remaining[i] = (s == null) ? 0 : s.getCount();
        }

        for (var req : inputItems) {
            int needed = req.count();
            if (needed <= 0) continue;

            for (int i = 0; i < provided.size() && needed > 0; i++) {
                var s = provided.get(i);
                if (s == null || s.isEmpty() || remaining[i] <= 0) continue;
                if (req.ingredient().test(s)) {
                    int take = Math.min(remaining[i], needed);
                    remaining[i] -= take;
                    needed -= take;
                }
            }

            if (needed > 0) return false;
        }

        FluidStack providedFluid = recipeInput.fluid();

        if (inputFluid == null) return true;
        if (providedFluid == null || providedFluid.isEmpty()) return false;
        if (!inputFluid.test(providedFluid)) return false;
        return providedFluid.getAmount() >= inputFluid.amount();
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public ItemStack assemble(Input inv) {
        return itemOutput();
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
        return NERecipeTypes.INTEGRATED_WORKING_STATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<Input>> getType() {
        return NERecipeTypes.INTEGRATED_WORKING_STATION.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    public boolean hasItemOutput() {
        return itemOutputTemplate != null;
    }

    public ItemStack itemOutput() {
        return itemOutputTemplate == null ? ItemStack.EMPTY : itemOutputTemplate.create();
    }

    public Optional<ItemStackTemplate> optionalItemOutputTemplate() {
        return Optional.ofNullable(itemOutputTemplate);
    }

    public boolean hasFluidOutput() {
        return fluidOutputTemplate != null;
    }

    public FluidStack fluidOutput() {
        return fluidOutputTemplate == null ? FluidStack.EMPTY : fluidOutputTemplate.create();
    }

    public Optional<FluidStackTemplate> optionalFluidOutputTemplate() {
        return Optional.ofNullable(fluidOutputTemplate);
    }

    public static final MapCodec<IntegratedWorkingStationRecipe> CODEC = RecordCodecBuilder.mapCodec(ins -> ins.group(
        SizedIngredient.NESTED_CODEC.listOf(0, 9).optionalFieldOf("inputItems", List.of()).forGetter(IntegratedWorkingStationRecipe::inputItems),
        SizedFluidIngredient.CODEC.optionalFieldOf("inputFluid", null).forGetter(IntegratedWorkingStationRecipe::inputFluid),
        ItemStackTemplate.CODEC.optionalFieldOf("itemOutput").forGetter(IntegratedWorkingStationRecipe::optionalItemOutputTemplate),
        FluidStackTemplate.CODEC.optionalFieldOf("fluidOutput").forGetter(IntegratedWorkingStationRecipe::optionalFluidOutputTemplate),
        Codec.INT.fieldOf("energy").forGetter(IntegratedWorkingStationRecipe::energy)
    ).apply(ins, (inputItems, inputFluid, itemOutput, fluidOutput, energy) ->
        new IntegratedWorkingStationRecipe(inputItems, inputFluid, itemOutput.orElse(null), fluidOutput.orElse(null), energy)
    ));

    public static final StreamCodec<RegistryFriendlyByteBuf, IntegratedWorkingStationRecipe> STREAM_CODEC = StreamCodec.composite(
        SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list(9)),
        IntegratedWorkingStationRecipe::inputItems,
        SizedFluidIngredient.STREAM_CODEC,
        IntegratedWorkingStationRecipe::inputFluid,
        ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC),
        IntegratedWorkingStationRecipe::optionalItemOutputTemplate,
        ByteBufCodecs.optional(FluidStackTemplate.STREAM_CODEC),
        IntegratedWorkingStationRecipe::optionalFluidOutputTemplate,
        ByteBufCodecs.VAR_INT,
        IntegratedWorkingStationRecipe::energy,
        (inputItems, inputFluid, itemOutput, fluidOutput, energy) ->
            new IntegratedWorkingStationRecipe(inputItems, inputFluid, itemOutput.orElse(null), fluidOutput.orElse(null), energy)
    );

    public static final RecipeSerializer<IntegratedWorkingStationRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    public record Input(List<ItemStack> inputs, @Nullable FluidStack fluid) implements RecipeInput {

        @Override
        public ItemStack getItem(int index) {
            return inputs.get(index);
        }

        @Override
        public int size() {
            return inputs.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack input : inputs) {
                if (!input.isEmpty()) {
                    return false;
                }
            }
            return fluid == null || fluid.isEmpty();
        }
    }
}
