package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Explicit upstream capabilities. Never recursively imports another mod's combined recipe catalog. */
final class LargeWorkstationRecipeSources {
    private static final ResourceLocation REACTION = ResourceLocation.parse("advanced_ae:reaction");
    private static final ResourceLocation LIGHTNING = ResourceLocation.parse("ae2lt:overload_processing");
    private static final ResourceLocation DATA_SEED = ResourceLocation.parse("ae2cs:data_reassembler/data_crystal_seed");
    private static final ResourceLocation DATA_REASSEMBLER = ResourceLocation.parse("data_energistics:data_reassembler");

    private LargeWorkstationRecipeSources() {}

    static LargeWorkstationRecipeGroup.Variant direct(LargeWorkstationRecipe recipe) {
        var module = ECOWorkstationModules.fromSourceType(recipe.sourceType());
        boolean unrestricted = !module.custom() && (module.legacy() == LargeWorkstationModule.REGULAR
            || module.legacy() == LargeWorkstationModule.OTHER);
        Set<ResourceLocation> modules = new HashSet<>();
        if (!unrestricted) modules.add(module.id());
        // This one recipe is authored as a Crystal Science/Data Energistics integration.
        // Its ID alone must never grant the Crystal Science card the entire data reassembler family.
        if (recipe.id().equals(DATA_SEED) && recipe.sourceType().equals(DATA_REASSEMBLER) && loaded("ae2cs")) {
            modules.add(ECOWorkstationModules.builtin(LargeWorkstationModule.CRYSTAL_SCIENCE).id());
        }
        return new LargeWorkstationRecipeGroup.Variant(recipe, recipe.id(), recipe.sourceType(), modules, unrestricted, false);
    }

    @Nullable
    static LargeWorkstationRecipeGroup.Variant borrowed(RecipeHolder<?> holder, LargeWorkstationRecipe recipe) {
        // The upstream adapter rejects subclasses because they may add matching/output rules.
        if (!recipe.sourceType().equals(REACTION) || !loaded("ae2lt")
            || !holder.value().getClass().getName().equals("net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe")) return null;
        // Probe the public capability rather than assuming all installed AE2LT versions can borrow reactions.
        return LightningBorrowing.supported() ? borrowReaction(recipe) : null;
    }

    static LargeWorkstationRecipeGroup.Variant borrowReaction(LargeWorkstationRecipe source) {
        if (!source.sourceType().equals(REACTION) || source.energy() < 5
            || source.display().inputItems().size() > 9 || !source.extraInputs().isEmpty()
            || !source.additionalFluidInputs().isEmpty() || !source.additionalOutputs().isEmpty()) {
            throw new IllegalArgumentException("Unsupported borrowed reaction contract");
        }
        var id = ResourceLocation.fromNamespaceAndPath("neoecoae", "workstation_borrowed/ae2lt/"
            + source.id().getNamespace() + "/" + source.id().getPath());
        // Upstream passes ReactionChamberRecipe.getEnergy() into the factory's FE budget unchanged.
        // Mirror that budget once, then retain the source fluid predicate and all material quantities.
        long energy = source.energy();
        var display = source.display();
        var borrowedDisplay = new IntegratedWorkingStationRecipe(display.inputItems(), display.inputFluid(),
            display.itemOutput(), display.fluidOutput(), (int) Math.min(Integer.MAX_VALUE, energy));
        int ticks = LightningWorkstationTiming.ticks(LIGHTNING.toString(), energy);
        var recipe = new LargeWorkstationRecipe(id, borrowedDisplay, 0,
            List.of(new GenericStack(com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE, 1)), LIGHTNING)
            .processing(ticks, energy, false);
        return new LargeWorkstationRecipeGroup.Variant(recipe, source.id(), source.sourceType(),
            Set.of(ECOWorkstationModules.fromSourceType(LIGHTNING).id()), false, true);
    }

    private static boolean loaded(String mod) {
        var mods = ModList.get();
        return mods != null && mods.isLoaded(mod);
    }

    private static final class LightningBorrowing {
        private static final boolean SUPPORTED = probe();

        static boolean supported() { return SUPPORTED; }

        private static boolean probe() {
            try {
                // Presence of this exact factory is the upstream borrowing contract used above.
                com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe.class.getMethod("borrowed",
                    List.class, net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient.class,
                    List.class, net.neoforged.neoforge.fluids.FluidStack.class, long.class);
                return true;
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return false;
            }
        }
    }
}
