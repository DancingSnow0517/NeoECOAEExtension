package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;
import java.util.List;

/** Optional classes are resolved inside their family adapter only after the recipe type gate. */
public final class LargeWorkstationRecipeAdapters {
    private LargeWorkstationRecipeAdapters() {}

    public static LargeWorkstationRecipe adapt(Recipe<?> source, String type) {
        return switch (type) {
            case "ae2cs:circuit_etcher_recipe", "ae2cs:crystal_aggregator_recipe", "ae2cs:crystal_pulverizer_recipe" -> CrystalScienceWorkstationRecipes.adapt(source, type);
            case "advanced_ae:reaction" -> Advanced.adapt(source);
            case "ae2lt:overload_processing", "ae2lt:lightning_assembly", "ae2lt:lightning_simulation" -> Lightning.adapt(source, type);
            default -> throw new IllegalArgumentException("Unsupported recipe type: " + type);
        };
    }

    private static SizedFluidIngredient noFluid() { return new SizedFluidIngredient(FluidIngredient.empty(), 1); }

    private static final class Advanced {
        static LargeWorkstationRecipe adapt(Recipe<?> source) {
            if (!(source instanceof net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe recipe)) {
                throw new IllegalArgumentException("Unexpected AdvancedAE recipe implementation");
            }
            var items = recipe.getInputs().stream().filter(input -> !input.isEmpty())
                .map(input -> new SizedIngredient(input.getIngredient(), input.getAmount())).toList();
            var inputFluid = recipe.getFluid();
            var fluid = inputFluid == null || inputFluid.isEmpty() ? noFluid()
                : SizedFluidIngredient.of(inputFluid.getIngredient(), inputFluid.getAmount());
            ItemStack itemResult = ItemStack.EMPTY;
            FluidStack fluidResult = FluidStack.EMPTY;
            int amount = Math.toIntExact(recipe.output.amount());
            if (amount <= 0) throw new IllegalArgumentException("Nonpositive AdvancedAE output");
            if (recipe.output.what() instanceof AEItemKey item) itemResult = item.toStack(amount);
            else if (recipe.output.what() instanceof AEFluidKey key) fluidResult = key.toStack(amount);
            else throw new IllegalArgumentException("Unsupported AdvancedAE output key");
            return wrap(source, "advanced_ae:reaction", items, fluid, itemResult, fluidResult, recipe.getEnergy(), List.of())
                .processing(100, feEquivalent(recipe.getEnergy()), recipe.getEnergy() > 0);
        }
    }

    private static final class Lightning {
        static LargeWorkstationRecipe adapt(Recipe<?> source, String type) {
            if (source instanceof com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe recipe) {
                if (!type.equals("ae2lt:overload_processing")) throw new IllegalArgumentException("Mismatched AE2LT recipe type");
                var outputs = recipe.itemResults();
                if (outputs.size() > 1) throw new IllegalArgumentException("Multiple AE2LT item outputs need a wider output contract");
                var items = recipe.itemInputs().stream().map(input -> new SizedIngredient(input.ingredient(), input.count())).toList();
                FluidStack input = recipe.fluidInput();
                if (input.isEmpty() && recipe.inputFluidAmount() > 0) {
                    throw new IllegalArgumentException("Borrowed fluid predicates require their own exact adapter");
                }
                return wrapFe(source, type, items, input.isEmpty() ? noFluid() : SizedFluidIngredient.of(input),
                    outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0), recipe.fluidResult(), recipe.totalEnergy(),
                    lightning(recipe.lightningTier(), recipe.lightningCost()));
            }
            if (source instanceof com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe recipe) {
                if (!type.equals("ae2lt:lightning_assembly")) throw new IllegalArgumentException("Mismatched AE2LT recipe type");
                return wrapFe(source, type, recipe.inputs().stream().map(input -> new SizedIngredient(input.ingredient(), input.count())).toList(),
                    noFluid(), recipe.getResultStack(), FluidStack.EMPTY, recipe.totalEnergy(),
                    lightning(recipe.lightningTier(), recipe.lightningCost()));
            }
            if (source instanceof com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe recipe) {
                if (!type.equals("ae2lt:lightning_simulation")) throw new IllegalArgumentException("Mismatched AE2LT recipe type");
                return wrapFe(source, type, recipe.inputs().stream().map(input -> new SizedIngredient(input.ingredient(), input.count())).toList(),
                    noFluid(), recipe.getResultStack(), FluidStack.EMPTY, recipe.totalEnergy(),
                    lightning(recipe.lightningTier(), recipe.lightningCost()));
            }
            throw new IllegalArgumentException("Unexpected AE2LT recipe implementation");
        }

        private static List<GenericStack> lightning(com.moakiee.ae2lt.me.key.LightningKey.Tier tier, int amount) {
            if (amount <= 0) throw new IllegalArgumentException("Nonpositive lightning cost");
            return List.of(new GenericStack(com.moakiee.ae2lt.me.key.LightningKey.of(tier), amount));
        }
    }

    private static LargeWorkstationRecipe wrapFe(Recipe<?> source, String type, List<SizedIngredient> items,
            SizedFluidIngredient fluid, ItemStack output, FluidStack fluidOutput, long fe, List<GenericStack> extras) {
        return wrap(source, type, items, fluid, output, fluidOutput, 0, extras)
            .processing(LightningWorkstationTiming.ticks(type, fe), fe, false);
    }

    static long feEquivalent(long ae) {
        return WorkstationEnergy.feEquivalent(ae);
    }

    private static LargeWorkstationRecipe wrap(Recipe<?> source, String type, List<SizedIngredient> items,
            SizedFluidIngredient fluid, ItemStack output, FluidStack fluidOutput, long energy, List<GenericStack> extras) {
        if (output.isEmpty() && fluidOutput.isEmpty()) throw new IllegalArgumentException("Recipe has no output");
        var display = new IntegratedWorkingStationRecipe(items, fluid, output, fluidOutput,
            (int) Math.min(Integer.MAX_VALUE, energy), source.getId());
        return new LargeWorkstationRecipe(source.getId(), display, energy, extras, new ResourceLocation(type));
    }
}
