package cn.dancingsnow.neoecoae.recipe;

import appeng.api.ids.AEComponents;
import appeng.api.storage.StorageCells;
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
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record IntegratedWorkingStationRecipe(
    List<SizedIngredient> inputItems,
    SizedFluidIngredient inputFluid,
    ItemStack itemOutput,
    FluidStack fluidOutput,
    int energy,
    boolean exactItemInputs,
    boolean requiresEmptyStorageCells
) implements Recipe<IntegratedWorkingStationRecipe.Input> {

    public static IntegratedWorkingStationRecipeBuilder builder() {
        return new IntegratedWorkingStationRecipeBuilder();
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
                    if (requiresEmptyStorageCells && !isPristineEmptyStorageCell(s)) {
                        return false;
                    }
                    int take = Math.min(remaining[i], needed);
                    remaining[i] -= take;
                    needed -= take;
                }
            }

            if (needed > 0) return false;
        }

        if (exactItemInputs) {
            for (int count : remaining) {
                if (count != 0) return false;
            }
        }

        FluidStack providedFluid = recipeInput.fluid();

        if (inputFluid.ingredient().isEmpty()) return true;
        if (providedFluid == null || providedFluid.isEmpty()) return false;
        if (!inputFluid.test(providedFluid)) return false;
        return providedFluid.getAmount() >= inputFluid.amount();
    }

    private static boolean isPristineEmptyStorageCell(ItemStack stack) {
        var inventory = StorageCells.getCellInventory(stack, null);
        if (inventory == null || !inventory.getAvailableStacks().isEmpty()) {
            return false;
        }
        if (!stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of()).isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof appeng.api.storage.cells.ICellWorkbenchItem workbenchItem) {
            for (var upgrade : workbenchItem.getUpgrades(stack)) {
                if (!upgrade.isEmpty()) return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(Input inv, HolderLookup.@NotNull Provider registries) {
        return getResultItem(registries).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return itemOutput;
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

    private int recipeFlags() {
        return (exactItemInputs ? 1 : 0) | (requiresEmptyStorageCells ? 2 : 0);
    }

    public static class Serializer implements RecipeSerializer<IntegratedWorkingStationRecipe> {
        public static final MapCodec<IntegratedWorkingStationRecipe> CODEC = RecordCodecBuilder.mapCodec(ins -> ins.group(
            SizedIngredient.FLAT_CODEC.listOf(0, 9).optionalFieldOf("inputItems", List.of()).forGetter(IntegratedWorkingStationRecipe::inputItems),
            SizedFluidIngredient.FLAT_CODEC.optionalFieldOf("inputFluid", new SizedFluidIngredient(FluidIngredient.empty(), 1)).forGetter(IntegratedWorkingStationRecipe::inputFluid),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("itemOutput", ItemStack.EMPTY).forGetter(IntegratedWorkingStationRecipe::itemOutput),
            FluidStack.OPTIONAL_CODEC.optionalFieldOf("fluidOutput", FluidStack.EMPTY).forGetter(IntegratedWorkingStationRecipe::fluidOutput),
            Codec.INT.fieldOf("energy").forGetter(IntegratedWorkingStationRecipe::energy),
            Codec.BOOL.optionalFieldOf("exactItemInputs", false).forGetter(IntegratedWorkingStationRecipe::exactItemInputs),
            Codec.BOOL.optionalFieldOf("requiresEmptyStorageCells", false).forGetter(IntegratedWorkingStationRecipe::requiresEmptyStorageCells)
        ).apply(ins, IntegratedWorkingStationRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, IntegratedWorkingStationRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list(9)),
            IntegratedWorkingStationRecipe::inputItems,
            SizedFluidIngredient.STREAM_CODEC,
            IntegratedWorkingStationRecipe::inputFluid,
            ItemStack.OPTIONAL_STREAM_CODEC,
            IntegratedWorkingStationRecipe::itemOutput,
            FluidStack.OPTIONAL_STREAM_CODEC,
            IntegratedWorkingStationRecipe::fluidOutput,
            ByteBufCodecs.VAR_INT,
            IntegratedWorkingStationRecipe::energy,
            ByteBufCodecs.VAR_INT,
            IntegratedWorkingStationRecipe::recipeFlags,
            (items, inputFluid, itemOutput, fluidOutput, energy, flags) -> new IntegratedWorkingStationRecipe(
                items, inputFluid, itemOutput, fluidOutput, energy, (flags & 1) != 0, (flags & 2) != 0
            )
        );

        @Override
        public MapCodec<IntegratedWorkingStationRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, IntegratedWorkingStationRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

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
