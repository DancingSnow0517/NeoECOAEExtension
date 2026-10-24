package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.recipes.entropy.EntropyRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Adapts inventory-backed AE2CS machine operations without linking optional mod classes. */
final class CrystalScienceWorkstationRecipes {
    static final ResourceLocation ENTROPY = ResourceLocation.parse("ae2cs:entropy_variation_reaction_chamber");

    private CrystalScienceWorkstationRecipes() {}

    static boolean isPresent() {
        return BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("ae2cs:entropy_variation_reaction_chamber"));
    }

    @Nullable
    static LargeWorkstationRecipe entropy(ResourceLocation id, EntropyRecipe recipe) {
        var input = recipe.getInput();
        var block = input.block().map(EntropyRecipe.BlockInput::block).orElse(Blocks.VOID_AIR);
        var fluid = input.fluid().map(EntropyRecipe.FluidInput::fluid).orElse(Fluids.EMPTY);
        // Inventory-backed machines cannot represent arbitrary block-state predicates.
        if (!recipe.matches(recipe.getMode(), block.defaultBlockState(), fluid.defaultFluidState())) return null;
        var items = new ArrayList<SizedIngredient>();
        if (input.block().isPresent()) {
            if (block.asItem() == Items.AIR) return null;
            items.add(new SizedIngredient(Ingredient.of(block), 1));
        }
        boolean snowballs = fluid == Fluids.FLOWING_WATER && recipe.getDrops().stream().anyMatch(drop -> drop.is(Items.SNOWBALL));
        var fluidInput = fluid == Fluids.EMPTY ? noFluid()
            : SizedFluidIngredient.of(sourceFluid(fluid), snowballs ? 250 : 1000);
        var outputs = new ArrayList<GenericStack>();
        recipe.getOutput().block().ifPresent(output -> {
            if (output.block().asItem() != Items.AIR) outputs.add(new GenericStack(AEItemKey.of(output.block().asItem()), 1));
        });
        recipe.getOutput().fluid().ifPresent(output -> {
            if (output.fluid() != Fluids.EMPTY) outputs.add(new GenericStack(AEFluidKey.of(sourceFluid(output.fluid())), 1000));
        });
        for (var drop : recipe.getDrops()) if (!drop.isEmpty()) outputs.add(GenericStack.fromItemStack(drop));
        if ((items.isEmpty() && fluidInput.ingredient().isEmpty()) || outputs.isEmpty()) return null;
        return wrap(id, items, fluidInput, outputs, 1600, ENTROPY);
    }

    private static Fluid sourceFluid(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    static SizedFluidIngredient noFluid() {
        return new SizedFluidIngredient(FluidIngredient.empty(), 1);
    }

    static LargeWorkstationRecipe wrap(ResourceLocation id, List<SizedIngredient> items, SizedFluidIngredient fluid,
            List<GenericStack> outputs, long energy, ResourceLocation sourceType) {
        ItemStack itemOutput = ItemStack.EMPTY;
        FluidStack fluidOutput = FluidStack.EMPTY;
        var additional = new ArrayList<GenericStack>();
        for (var output : outputs) {
            if (output.what() instanceof AEItemKey item && itemOutput.isEmpty()) itemOutput = item.toStack(Math.toIntExact(output.amount()));
            else if (output.what() instanceof AEFluidKey key && fluidOutput.isEmpty()) fluidOutput = key.toStack(Math.toIntExact(output.amount()));
            else additional.add(output);
        }
        var display = new IntegratedWorkingStationRecipe(items, fluid, itemOutput, fluidOutput, (int) Math.min(Integer.MAX_VALUE, energy));
        return new LargeWorkstationRecipe(id, display, energy, List.of(), sourceType, List.of(), additional);
    }
}
