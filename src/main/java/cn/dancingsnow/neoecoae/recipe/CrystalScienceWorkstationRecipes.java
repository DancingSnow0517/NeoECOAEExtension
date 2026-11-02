package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.recipes.entropy.EntropyRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Adapts inventory-backed AE2CS machine operations without linking optional mod classes. */
final class CrystalScienceWorkstationRecipes {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrystalScienceWorkstationRecipes.class);
    static final ResourceLocation ENTROPY = ResourceLocation.parse("ae2cs:entropy_variation_reaction_chamber");
    static final ResourceLocation GROWTH = ResourceLocation.parse("ae2cs:crystal_growth");
    private static final ResourceLocation GROWTH_CHAMBER = ResourceLocation.parse("ae2cs:crystal_growth_chamber");
    private static final TagKey<Item> CRYSTAL_SEEDS = TagKey.create(Registries.ITEM, ResourceLocation.parse("ae2cs:crystal_seeds"));
    private static final String SEED_CLASS = "io.github.lounode.ae2cs.common.item.CrystalSeedItem";

    private CrystalScienceWorkstationRecipes() {}

    static boolean isPresent() {
        return BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("ae2cs:entropy_variation_reaction_chamber"));
    }

    /** Growth is a seed capability, not a RecipeManager recipe type. Tags define the chamber's inputs. */
    static List<LargeWorkstationRecipe> growthRecipes() {
        if (!BuiltInRegistries.ITEM.containsKey(GROWTH_CHAMBER)) return List.of();
        var seeds = new ArrayList<Item>();
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(CRYSTAL_SEEDS)) seeds.add(holder.value());
        return growthRecipes(seeds);
    }

    static List<LargeWorkstationRecipe> growthRecipes(Iterable<Item> seeds) {
        var recipes = new ArrayList<LargeWorkstationRecipe>();
        for (var seed : seeds) {
            // Subclasses may override progress rules that the inventory contract cannot represent.
            if (!seed.getClass().getName().equals(SEED_CLASS)) continue;
            try {
                Item output = (Item) seed.getClass().getMethod("getGrowTo").invoke(seed);
                int growthTicks = (Integer) seed.getClass().getMethod("getOvergrowTick").invoke(seed);
                recipes.add(growth(seed, output, growthTicks));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                LOGGER.warn("Cannot adapt crystal growth for {}", BuiltInRegistries.ITEM.getKey(seed), failure);
            }
        }
        return List.copyOf(recipes);
    }

    static LargeWorkstationRecipe growth(Item seed, Item output, int growthTicks) {
        if (seed == Items.AIR || output == null || output == Items.AIR || output == seed || growthTicks <= 0) {
            throw new IllegalArgumentException("Invalid crystal growth contract");
        }
        var seedId = BuiltInRegistries.ITEM.getKey(seed);
        var id = ResourceLocation.fromNamespaceAndPath("neoecoae", "crystal_growth/"
            + seedId.getNamespace() + "/" + seedId.getPath());
        // An unupgraded chamber advances all seeds by 10 growth ticks every 10 ticks for 100 AE.
        // Charge one complete cycle per seed operation; partially grown seeds receive no discount.
        long cycles = ((long) growthTicks + 9) / 10;
        long energy = Math.multiplyExact(cycles, 100);
        int ticks = (int) Math.min(Integer.MAX_VALUE, cycles * 10);
        var display = new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(Ingredient.of(seed), 1)),
            noFluid(), new ItemStack(output), FluidStack.EMPTY, (int) Math.min(Integer.MAX_VALUE, energy), ticks);
        return new LargeWorkstationRecipe(id, display, energy, List.of(), GROWTH).processing(ticks, 0, false);
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
