package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.menu.AutoCraftingMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Shared crafting and validation logic used by the cold path and FastPath warmup. */
public final class ECOFastPathValidator {
    private ECOFastPathValidator() {
    }

    public static Optional<CraftingOutcome> craft(
        ECOExtractedPatternExecution execution,
        Level level
    ) {
        return craft(execution, level, new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3));
    }

    /**
     * Crafts into the supplied scratch inventory. The inventory remains populated so the normal
     * request path can still post its crafting event with the exact grid used for assembly.
     */
    public static Optional<CraftingOutcome> craft(
        ECOExtractedPatternExecution execution,
        Level level,
        TransientCraftingContainer craftingInventory
    ) {
        IMolecularAssemblerSupportedPattern pattern = execution.molecularPattern();
        if (pattern == null) {
            return Optional.empty();
        }

        craftingInventory.clearContent();
        pattern.fillCraftingGrid(execution.craftingContainer(), craftingInventory::setItem);
        ItemStack output = pattern.assemble(craftingInventory.asCraftInput(), level);
        if (output.isEmpty()) {
            craftingInventory.clearContent();
            return Optional.empty();
        }

        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : pattern.getRemainingItems(craftingInventory.asCraftInput())) {
            if (!item.isEmpty()) {
                remaining.add(item.copy());
            }
        }

        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 0; slot < craftingInventory.getContainerSize(); slot++) {
            ItemStack item = craftingInventory.getItem(slot);
            if (!item.isEmpty()) {
                inputs.add(item.copy());
            }
        }
        return Optional.of(new CraftingOutcome(output.copy(), inputs, remaining));
    }

    /**
     * Compares one concrete assembly with the extracted execution metadata. This is intentionally
     * independent of cache ownership so warmup and a cold crafting request share one rule set.
     */
    public static ValidationResult validate(
        ECOExtractedPatternExecution execution,
        CraftingOutcome outcome
    ) {
        if (execution.key() == null || !execution.fastPathEligible()) {
            return ValidationResult.notApplicable();
        }

        var outputEntries = ECOFastPathStacks.fromItemStack(outcome.output());
        var inputEntries = ECOFastPathStacks.fromItemStacks(outcome.inputs());
        var remainingEntries = ECOFastPathStacks.fromItemStacks(outcome.remaining());
        if (outputEntries.isEmpty() || inputEntries.isEmpty() || remainingEntries.isEmpty()) {
            return ValidationResult.rejected(ECOFastPathFallbackReason.RUNTIME_STACK_CONVERSION_FAILED);
        }
        if (!outputEntries.get().equals(execution.expectedOutputs())) {
            return ValidationResult.rejected(ECOFastPathFallbackReason.OUTPUT_MISMATCH);
        }
        if (!remainingEntries.get().equals(execution.expectedContainerItems())) {
            return ValidationResult.rejected(ECOFastPathFallbackReason.CONTAINER_MISMATCH);
        }
        if (!inputEntries.get().equals(execution.inputItems())) {
            return ValidationResult.rejected(ECOFastPathFallbackReason.INPUT_MISMATCH);
        }
        if (!ECOBatchCraftingHelper.areValidPersistedItemStacks(
                outputEntries.get(), Integer.MAX_VALUE, true)
            || !ECOBatchCraftingHelper.areValidPersistedItemStacks(
                remainingEntries.get(), Integer.MAX_VALUE, false)
            || !ECOBatchCraftingHelper.areValidPersistedItemStacks(
                inputEntries.get(), Integer.MAX_VALUE, false)
            || !ECOFastPathStacks.isSafeForFastPath(
                outputEntries.get(), remainingEntries.get(), inputEntries.get())) {
            return ValidationResult.rejected(ECOFastPathFallbackReason.CACHE_VALIDATION_REJECTED);
        }
        return ValidationResult.accepted(outputEntries.get(), remainingEntries.get(), inputEntries.get());
    }

    public record CraftingOutcome(
        ItemStack output,
        List<ItemStack> inputs,
        List<ItemStack> remaining
    ) {
        public CraftingOutcome {
            output = output.copy();
            inputs = copyStacks(inputs);
            remaining = copyStacks(remaining);
        }

        private static List<ItemStack> copyStacks(List<ItemStack> source) {
            List<ItemStack> copy = new ArrayList<>(source.size());
            for (ItemStack stack : source) {
                if (!stack.isEmpty()) {
                    copy.add(stack.copy());
                }
            }
            return List.copyOf(copy);
        }
    }

    public record ValidationResult(
        boolean applicable,
        boolean accepted,
        List<appeng.api.stacks.GenericStack> outputs,
        List<appeng.api.stacks.GenericStack> remaining,
        List<appeng.api.stacks.GenericStack> inputs,
        @Nullable ECOFastPathFallbackReason rejectionReason
    ) {
        private static ValidationResult notApplicable() {
            return new ValidationResult(false, false, List.of(), List.of(), List.of(), null);
        }

        private static ValidationResult rejected(ECOFastPathFallbackReason reason) {
            return new ValidationResult(true, false, List.of(), List.of(), List.of(), reason);
        }

        private static ValidationResult accepted(
            List<appeng.api.stacks.GenericStack> outputs,
            List<appeng.api.stacks.GenericStack> remaining,
            List<appeng.api.stacks.GenericStack> inputs
        ) {
            return new ValidationResult(true, true, outputs, remaining, inputs, null);
        }
    }
}
