package cn.dancingsnow.neoecoae.crafting.execution;

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
            expectedContainerItems, remainderCache, true, null);
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
            expectedContainerItems, remainderCache, false, null);
    }

    @Nullable
    static KeyCounter[] extractPatternInputsFromDisposablePreview(
            IPatternDetails details,
            ECOCraftingInputPreview sourceInventory,
            Level level,
            ECOCraftingInputBuffer buffer,
            ECOCraftingRemainderCache remainderCache) {
        var inputs = buffer.prepare(details.getInputs().length);
        return extractPatternInputs(details, sourceInventory, level, buffer.outputs(),
            buffer.remainders(), remainderCache, false, inputs);
    }

    @Nullable
    private static KeyCounter[] extractPatternInputs(
            IPatternDetails details,
            ICraftingInventory sourceInventory,
            Level level,
            KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems,
            ECOCraftingRemainderCache remainderCache,
            boolean rollbackOnFailure,
            @Nullable KeyCounter[] reusableInputs) {
        var inputs = details.getInputs();
        KeyCounter[] inputHolder = reusableInputs == null ? new KeyCounter[inputs.length] : reusableInputs;
        boolean found = true;

        for (int slot = 0; slot < inputs.length; slot++) {
            var input = inputs[slot];
            var extractedInputs = inputHolder[slot];
            if (extractedInputs == null) extractedInputs = inputHolder[slot] = new KeyCounter();
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
            var preview = sourceInventory instanceof ECOCraftingInputPreview overlay ? overlay : null;
            var templates = preview == null
                ? CraftingCpuHelper.getValidItemTemplates(sourceInventory, input, level)
                : preview.inputTemplates(input, level);
            for (var template : templates) {
                if (preview != null && preview.needsTemplateValidation() && !input.isValid(template.key(), level)) {
                    continue;
                }
                long extracted = preview == null
                    ? CraftingCpuHelper.extractTemplates(sourceInventory, template, remainingMultiplier)
                    : preview.extractTemplates(template, remainingMultiplier);
                if (extracted <= 0L) continue;
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
