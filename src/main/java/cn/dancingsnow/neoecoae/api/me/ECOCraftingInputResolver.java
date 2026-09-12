package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** ECO dispatch equivalent of AE2 input extraction with reload-aware remainder memoization. */
final class ECOCraftingInputResolver {
    private ECOCraftingInputResolver() {
    }

    @Nullable
    static KeyCounter[] extractPatternInputs(
            IPatternDetails details,
            ICraftingInventory sourceInventory,
            Level level,
            KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems,
            ECOCraftingRemainderCache remainderCache) {
        return extractPatternInputs(details, sourceInventory, level, expectedOutputs,
            expectedContainerItems, remainderCache, true);
    }

    /** Resolve against a fresh overlay whose virtual removals are discarded after a failed attempt. */
    @Nullable
    static KeyCounter[] extractPatternInputsFromDisposablePreview(
            IPatternDetails details,
            ECOCraftingInputPreview sourceInventory,
            Level level,
            KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems,
            ECOCraftingRemainderCache remainderCache) {
        return extractPatternInputs(details, sourceInventory, level, expectedOutputs,
            expectedContainerItems, remainderCache, false);
    }

    @Nullable
    private static KeyCounter[] extractPatternInputs(
            IPatternDetails details,
            ICraftingInventory sourceInventory,
            Level level,
            KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems,
            ECOCraftingRemainderCache remainderCache,
            boolean rollbackOnFailure) {
        var inputs = details.getInputs();
        KeyCounter[] inputHolder = new KeyCounter[inputs.length];
        boolean found = true;

        for (int slot = 0; slot < inputs.length; slot++) {
            var input = inputs[slot];
            var extractedInputs = inputHolder[slot] = new KeyCounter();
            long remainingMultiplier = input.getMultiplier();
            if (sourceInventory instanceof ECOCraftingInputPreview preview) {
                long primaryCrafts = preview.extractPrimaryInput(input, remainingMultiplier, level,
                    extractedInputs, expectedContainerItems, remainderCache);
                if (primaryCrafts >= 0L) {
                    remainingMultiplier -= primaryCrafts;
                    if (remainingMultiplier > 0L) {
                        found = false;
                        break;
                    }
                    continue;
                }
            }
            for (var template : CraftingCpuHelper.getValidItemTemplates(sourceInventory, input, level)) {
                long extracted = CraftingCpuHelper.extractTemplates(
                    sourceInventory, template, remainingMultiplier);
                extractedInputs.add(template.key(), extracted * template.amount());

                var remainder = remainderCache.get(input, template.key());
                if (remainder != null) {
                    expectedContainerItems.add(remainder, extracted);
                }

                remainingMultiplier -= extracted;
                if (remainingMultiplier == 0L) {
                    break;
                }
            }

            if (remainingMultiplier > 0L) {
                found = false;
                break;
            }
        }

        if (!found && rollbackOnFailure) {
            CraftingCpuHelper.reinjectPatternInputs(sourceInventory, inputHolder);
        }
        if (!found) {
            return null;
        }

        for (var output : details.getOutputs()) {
            expectedOutputs.add(output.what(), output.amount());
        }
        return inputHolder;
    }
}
