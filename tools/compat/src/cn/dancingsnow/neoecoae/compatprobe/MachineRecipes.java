package cn.dancingsnow.neoecoae.compatprobe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Resolves final datapack recipes; never registers replacement recipes. */
final class MachineRecipes {
    final Map<AEKey, Long> inputs = new LinkedHashMap<>();
    final AEKey target;
    final long output;
    final ItemStack encoded;
    final String id;
    final IntegratedWorkingStationRecipe workstation;

    MachineRecipes(NeoMachineRig rig, String targetId, boolean crafting) {
        target = targetId.startsWith("fluid:") ? NeoMachineRig.fluid(targetId.substring(6)) : NeoMachineRig.item(targetId);
        if (crafting) {
            var candidates = rig.level.getRecipeManager().getRecipes().stream().filter(recipe -> recipe instanceof CraftingRecipe)
                    .map(recipe -> (CraftingRecipe) recipe).filter(recipe -> !recipe.getResultItem(rig.level.registryAccess()).isEmpty()
                            && AEItemKey.of(recipe.getResultItem(rig.level.registryAccess())).equals(target)).toList();
            NeoMachineRig.check(candidates.size() == 1, "Expected one final crafting recipe for " + targetId + "; found " + candidates.stream().map(CraftingRecipe::getId).toList());
            var source = candidates.get(0);
            var grid = new ItemStack[9];
            java.util.Arrays.fill(grid, ItemStack.EMPTY);
            for (int slot = 0; slot < source.getIngredients().size(); slot++) {
                var ingredient = source.getIngredients().get(slot);
                if (ingredient.isEmpty()) continue;
                NeoMachineRig.check(ingredient.getItems().length > 0, "Empty crafting ingredient " + source.getId());
                var stack = ingredient.getItems()[0].copy();
                stack.setCount(1);
                int index = source instanceof ShapedRecipe shaped ? slot / shaped.getWidth() * 3 + slot % shaped.getWidth() : slot;
                grid[index] = stack;
                inputs.merge(AEItemKey.of(stack), 1L, Math::addExact);
            }
            var result = source.getResultItem(rig.level.registryAccess());
            output = result.getCount();
            encoded = PatternDetailsHelper.encodeCraftingPattern(source, grid, result, true, false);
            id = source.getId().toString();
            workstation = null;
        } else {
            var candidates = rig.level.getRecipeManager().getRecipes().stream()
                    .filter(recipe -> recipe instanceof IntegratedWorkingStationRecipe).map(recipe -> (IntegratedWorkingStationRecipe) recipe)
                    .filter(recipe -> recipe.hasItemOutput() ? AEItemKey.of(recipe.itemOutput()).equals(target)
                            : recipe.hasFluidOutput() && AEFluidKey.of(recipe.fluidOutput().getFluid(), recipe.fluidOutput().getTag()).equals(target)).toList();
            NeoMachineRig.check(candidates.size() == 1, "Expected one final NeoECO workstation recipe for " + targetId + "; found " + candidates.stream().map(IntegratedWorkingStationRecipe::id).toList());
            workstation = candidates.get(0);
            for (var ingredient : workstation.inputItems()) {
                NeoMachineRig.check(ingredient.ingredient().getItems().length > 0, "Empty GTL tag in " + workstation.id());
                inputs.merge(AEItemKey.of(ingredient.ingredient().getItems()[0]), (long) ingredient.count(), Math::addExact);
            }
            if (!workstation.inputFluid().ingredient().isEmpty()) {
                var fluids = workstation.inputFluid().ingredient().getStacks();
                NeoMachineRig.check(fluids.length > 0, "Empty workstation fluid ingredient");
                inputs.put(AEFluidKey.of(fluids[0].getFluid(), fluids[0].getTag()), (long) workstation.inputFluid().amount());
            }
            String expectedFluid = targetId.equals("neoecoae:crystal_ingot") ? "gtceu:argon" : "gtceu:distilled_water";
            long expectedAmount = targetId.equals("neoecoae:crystal_ingot") ? 2000 : targetId.startsWith("fluid:") ? 1000 : 250;
            NeoMachineRig.check(inputs.getOrDefault(NeoMachineRig.fluid(expectedFluid), 0L) == expectedAmount,
                    "The selected NeoECO recipe does not have the GTL profile's fluid contract: " + workstation.id());
            output = workstation.hasItemOutput() ? workstation.itemOutput().getCount() : workstation.fluidOutput().getAmount();
            encoded = PatternDetailsHelper.encodeProcessingPattern(inputs.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue()))
                    .toArray(GenericStack[]::new), new GenericStack[] {new GenericStack(target, output)});
            id = workstation.id().toString();
        }
    }

    IPatternDetails pattern(NeoMachineRig rig) { return PatternDetailsHelper.decodePattern(encoded, rig.level); }
    Map<AEKey, Long> scaled(long runs) {
        var totals = new LinkedHashMap<AEKey, Long>();
        inputs.forEach((key, amount) -> totals.put(key, Math.multiplyExact(amount, runs)));
        return totals;
    }
}
