package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Captures every mutable AE2 input needed by the ECO worker while still on the server thread. */
public final class ECOAE2SnapshotFactory {
    private static final int MAX_MATERIALS = 16_384;
    private static final int MAX_OPERATIONS = 65_536;

    private ECOAE2SnapshotFactory() {
    }

    public static Optional<ECOAE2PlanningSnapshot> capture(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy
    ) {
        if (requestedAmount <= 0 || strategy != CalculationStrategy.REPORT_MISSING_ITEMS) {
            return Optional.empty();
        }
        try {
            Map<AEKey, Long> inventory = copyInventory(grid, requester);
            // AE2 deliberately does not satisfy the final request from already stored output.
            inventory.remove(requestedKey);

            var craftingService = grid.getCraftingService();
            ArrayDeque<AEKey> pending = new ArrayDeque<>();
            Set<AEKey> visitedMaterials = new HashSet<>();
            Set<IPatternDetails> visitedPatterns = new HashSet<>();
            List<ECOPlanningOperation<AEKey, IPatternDetails>> operations = new ArrayList<>();
            Map<IPatternDetails, Integer> inputSlotCounts = new LinkedHashMap<>();
            boolean multiplePaths = false;
            pending.add(requestedKey);

            while (!pending.isEmpty()) {
                AEKey material = pending.removeFirst();
                if (!visitedMaterials.add(material)) {
                    continue;
                }
                if (visitedMaterials.size() > MAX_MATERIALS || craftingService.canEmitFor(material)) {
                    return Optional.empty();
                }
                var producers = List.copyOf(craftingService.getCraftingFor(material));
                multiplePaths |= producers.size() > 1;
                for (IPatternDetails details : producers) {
                    if (!visitedPatterns.add(details)) {
                        continue;
                    }
                    Optional<ECOPlanningOperation<AEKey, IPatternDetails>> operation = convert(
                        details,
                        material,
                        pending
                    );
                    if (operation.isEmpty()) {
                        return Optional.empty();
                    }
                    operations.add(operation.get());
                    inputSlotCounts.put(details, details.getInputs().length);
                    if (operations.size() > MAX_OPERATIONS) {
                        return Optional.empty();
                    }
                }
            }

            var problem = new ECOPlanningProblem<>(
                operations,
                inventory,
                Map.of(requestedKey, requestedAmount)
            );
            return Optional.of(new ECOAE2PlanningSnapshot(
                problem,
                requestedKey,
                requestedAmount,
                multiplePaths,
                inputSlotCounts
            ));
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Map<AEKey, Long> copyInventory(IGrid grid, ICraftingSimulationRequester requester) {
        KeyCounter source;
        var actionSource = requester.getActionSource();
        if (actionSource != null && actionSource.player().isPresent()) {
            source = grid.getStorageService().getInventory().getAvailableStacks();
        } else {
            source = grid.getStorageService().getCachedInventory();
        }
        Map<AEKey, Long> inventory = new LinkedHashMap<>();
        for (var entry : source) {
            if (entry.getLongValue() > 0) {
                inventory.put(entry.getKey(), entry.getLongValue());
            }
        }
        return inventory;
    }

    private static Optional<ECOPlanningOperation<AEKey, IPatternDetails>> convert(
        IPatternDetails details,
        AEKey primaryMaterial,
        ArrayDeque<AEKey> pending
    ) {
        GenericStack primaryOutput = details.getPrimaryOutput();
        if (primaryOutput == null || !primaryMaterial.equals(primaryOutput.what())) {
            return Optional.empty();
        }
        Map<AEKey, Long> inputs = new LinkedHashMap<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] choices = input.getPossibleInputs();
            if (choices.length != 1 || choices[0] == null || choices[0].amount() <= 0 || input.getMultiplier() <= 0) {
                return Optional.empty();
            }
            GenericStack template = choices[0];
            if (input.getRemainingKey(template.what()) != null) {
                return Optional.empty();
            }
            long amount = Math.multiplyExact(template.amount(), input.getMultiplier());
            inputs.merge(template.what(), amount, Math::addExact);
            pending.addLast(template.what());
        }

        Map<AEKey, Long> outputs = new LinkedHashMap<>();
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.amount() <= 0) {
                return Optional.empty();
            }
            outputs.merge(output.what(), output.amount(), Math::addExact);
        }
        if (outputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ECOPlanningOperation<>(details, inputs, outputs, Set.of(primaryMaterial)));
    }
}
