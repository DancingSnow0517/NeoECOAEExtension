package cn.dancingsnow.neoecoae.api.me;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedFastPathRecipe;

/** Captures physical input consumption and records outputs after provider ownership transfer. */
final class ECOCraftingAccounting {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private final Consumer<AEKey> postChange;
    private final Runnable markDirty;

    ECOCraftingAccounting(Consumer<AEKey> postChange, Runnable markDirty) {
        this.postChange = postChange;
        this.markDirty = markDirty;
    }

    static void validateRuntimeConsumption(ExecutingCraftingJob job, Map<AEKey, Long> consumed) {
        var runtime = job.runtimeExecutionState();
        if (runtime != null) runtime.validateActualConsumption(consumed);
    }

    static Map<AEKey, Long> consumedInputs(KeyCounter[] counters) {
        return mergeConsumedInputs(counters, List.of());
    }

    static Map<AEKey, Long> consumedInputs(List<GenericStack> stacks) {
        return mergeConsumedInputs(Map.of(), stacks);
    }

    static Map<AEKey, Long> mergeConsumedInputs(KeyCounter[] first, List<GenericStack> additional) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        if (first != null) {
            for (KeyCounter counter : first) {
                if (counter == null) continue;
                for (var entry : counter) addConsumedInput(result, entry.getKey(), entry.getLongValue());
            }
        }
        addConsumedInputs(result, additional);
        return Map.copyOf(result);
    }

    static Map<AEKey, Long> mergeConsumedInputs(Map<AEKey, Long> first, List<GenericStack> additional) {
        Map<AEKey, Long> result = new LinkedHashMap<>(first);
        addConsumedInputs(result, additional);
        return Map.copyOf(result);
    }

    private static void addConsumedInputs(Map<AEKey, Long> target, List<GenericStack> stacks) {
        if (stacks == null) return;
        for (GenericStack stack : stacks) {
            if (stack != null) addConsumedInput(target, stack.what(), stack.amount());
        }
    }

    private static void addConsumedInput(Map<AEKey, Long> target, AEKey key, long amount) {
        if (key != null && amount > 0L) target.merge(key, amount, Math::addExact);
    }

    static void reinjectPatternInputs(ListCraftingInventory inventory, KeyCounter[] container) {
        if (container != null) CraftingCpuHelper.reinjectPatternInputs(inventory, container);
    }

    void recordPushedPattern(ExecutingCraftingJob job, KeyCounter outputs, KeyCounter remainders) {
        for (var output : outputs) recordOutput(job, output.getKey(), output.getLongValue());
        for (var remainder : remainders) recordRemainder(job, remainder.getKey(), remainder.getLongValue());
        markDirty.run();
    }

    void recordPushedPattern(ExecutingCraftingJob job, ECOExtractedPatternExecution execution, long craftCount) {
        long multiplier = Math.max(1L, craftCount);
        for (var output : execution.expectedOutputs()) {
            recordOutput(job, output.what(), Math.multiplyExact(output.amount(), multiplier));
        }
        for (var remainder : execution.expectedContainerItems()) {
            recordRemainder(job, remainder.what(), Math.multiplyExact(remainder.amount(), multiplier));
        }
        markDirty.run();
    }

    void recordPushedBatchPattern(ExecutingCraftingJob job, ECOVerifiedFastPathRecipe recipe, long craftCount) {
        long multiplier = Math.max(1L, craftCount);
        for (var output : recipe.outputsPerCraft()) {
            recordOutput(job, output.what(), Math.multiplyExact(output.amount(), multiplier));
        }
        // Durable tools return the aggregate batch remainder, not a multiple of the one-craft remainder.
        for (var remainder : recipe.batchRemainders(multiplier)) {
            recordRemainder(job, remainder.what(), remainder.amount());
        }
        markDirty.run();
    }

    static void chargeAcceptedPatternEnergy(IEnergyService energyService, double requiredPower) {
        try {
            double charged = energyService.extractAEPower(
                requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG
            );
            if (Double.isNaN(charged) || charged < requiredPower - 0.01D) {
                LOGGER.error(
                    "Crafting pattern was accepted, but only {} of {} crafting energy was charged",
                    charged,
                    requiredPower
                );
            }
        } catch (RuntimeException e) {
            // The provider already owns the inputs. Accounting must continue so this pattern is not scheduled twice.
            LOGGER.error("Crafting pattern was accepted, but its crafting energy could not be charged", e);
        }
    }

    private void recordOutput(ExecutingCraftingJob job, AEKey key, long amount) {
        job.waitingFor.insert(key, amount, Actionable.MODULATE);
        postChange.accept(key);
    }

    private void recordRemainder(ExecutingCraftingJob job, AEKey key, long amount) {
        job.waitingFor.insert(key, amount, Actionable.MODULATE);
        job.timeTracker.addMaxItems(amount, key.getType());
        postChange.accept(key);
    }
}
