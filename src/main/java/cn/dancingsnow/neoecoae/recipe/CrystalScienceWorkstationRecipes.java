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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import net.minecraftforge.fluids.FluidStack;
import cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Adapts inventory-backed AE2CS machine operations without linking optional mod classes. */
final class CrystalScienceWorkstationRecipes {
    static final ResourceLocation ENTROPY = new ResourceLocation("ae2cs:entropy_variation_reaction_chamber");

    private CrystalScienceWorkstationRecipes() {}

    static LargeWorkstationRecipe adapt(Recipe<?> recipe, String type) {
        var inputs = type.equals("ae2cs:crystal_pulverizer_recipe")
            ? List.of(invoke(recipe, "input", Object.class)) : invoke(recipe, "required", List.class);
        var items = new ArrayList<SizedIngredient>();
        for (var input : inputs) {
            var ingredient = invoke(input, "ingredient", Ingredient.class);
            int count = invoke(input, "count", Integer.class);
            if (!ingredient.isEmpty()) items.add(new SizedIngredient(ingredient, count));
        }
        var result = invoke(recipe, "result", ItemStack.class);
        int energy = invoke(recipe, "energyCost", Integer.class);
        if (items.isEmpty() || result.isEmpty()) throw new IllegalArgumentException("Empty AE2CS recipe");
        return wrap(recipe.getId(), items, noFluid(), List.of(GenericStack.fromItemStack(result)), energy, new ResourceLocation(type));
    }

    private static <T> T invoke(Object value, String method, Class<T> result) {
        try {
            return result.cast(value.getClass().getMethod(method).invoke(value));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("Unsupported AE2CS recipe accessor: " + method, failure);
        }
    }

    static boolean isPresent() {
        return BuiltInRegistries.ITEM.containsKey(new ResourceLocation("ae2cs:entropy_variation_reaction_chamber"));
    }

    @Nullable
    static LargeWorkstationRecipe entropy(ResourceLocation id, EntropyRecipe recipe) {
        var block = recipe.getInputBlock() == null ? Blocks.VOID_AIR : recipe.getInputBlock();
        var fluid = recipe.getInputFluid() == null ? Fluids.EMPTY : recipe.getInputFluid();
        // Inventory-backed machines cannot represent arbitrary block-state predicates.
        if (!recipe.matches(recipe.getMode(), block.defaultBlockState(), fluid.defaultFluidState())) return null;
        var items = new ArrayList<SizedIngredient>();
        if (recipe.getInputBlock() != null) {
            if (block.asItem() == Items.AIR) return null;
            items.add(new SizedIngredient(Ingredient.of(block), 1));
        }
        var fluidInput = fluid == Fluids.EMPTY ? noFluid() : SizedFluidIngredient.of(new FluidStack(fluid, 1000));
        var outputs = new ArrayList<GenericStack>();
        if (recipe.getOutputBlock() != null && recipe.getOutputBlock().asItem() != Items.AIR) {
            outputs.add(new GenericStack(AEItemKey.of(recipe.getOutputBlock().asItem()), 1));
        }
        if (recipe.getOutputFluid() != null && recipe.getOutputFluid() != Fluids.EMPTY) {
            outputs.add(new GenericStack(AEFluidKey.of(recipe.getOutputFluid()), 1000));
        }
        for (var drop : recipe.getDrops()) if (!drop.isEmpty()) outputs.add(GenericStack.fromItemStack(drop));
        if ((items.isEmpty() && fluidInput.ingredient().isEmpty()) || outputs.isEmpty()) return null;
        return wrap(id, items, fluidInput, outputs, 1600, ENTROPY);
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
        var display = new IntegratedWorkingStationRecipe(items, fluid, itemOutput, fluidOutput, (int) Math.min(Integer.MAX_VALUE, energy), id);
        return new LargeWorkstationRecipe(id, display, energy, List.of(), sourceType, List.of(), additional);
    }
}
