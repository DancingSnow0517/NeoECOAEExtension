package cn.dancingsnow.neoecoae.impl.crafting.processingbatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;

import cn.dancingsnow.neoecoae.config.NEConfig;

/**
 * Adapter for AE2's standard Pattern Provider external-inventory route.
 *
 * <p>The adapter deliberately accepts only the base AE2 logic. Addon subclasses and dedicated
 * crafting machines remain on AE2's original single-craft call.</p>
 */
public final class ECOProcessingBatchAdapter {
    private ECOProcessingBatchAdapter() {
    }

    /**
     * Captures a target and returns a one-shot admission. No provider state is changed until
     * {@link ECOProcessingBatchAdmission#commit(KeyCounter[])} is called.
     */
    @Nullable
    public static ECOProcessingBatchAdmission prepare(
            ICraftingProvider provider,
            IPatternDetails patternDetails,
            KeyCounter[] prototype,
            long requestedCrafts) {
        if (!NEConfig.ecoProcessingBatchEnabled) {
            ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.DISABLED,
                    "processing-provider batching is disabled");
            return null;
        }
        if (provider == null || patternDetails == null || prototype == null || requestedCrafts < 2L) {
            return null;
        }
        if (!(provider instanceof PatternProviderLogic logic)
                || logic.getClass() != PatternProviderLogic.class
                || !(provider instanceof ECOPatternProviderBatchAccess access)) {
            ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.NOT_STANDARD_PROVIDER,
                    provider.getClass().getName());
            return null;
        }

        if (!access.neoecoae$getSendList().isEmpty()) {
            ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.BUSY,
                    "provider has pending input remainder");
            return null;
        }
        if (!access.neoecoae$getMainNode().isActive()) {
            ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.OFFLINE,
                    "provider grid node is inactive");
            return null;
        }
        if (!access.neoecoae$getPublishedPatterns().contains(patternDetails)) {
            ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.PATTERN_NOT_PUBLISHED,
                    "selected pattern is not published by this provider");
            return null;
        }
        if (logic.getCraftingLockedReason() != LockCraftingMode.NONE) {
            ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.LOCKED,
                    logic.getCraftingLockedReason().name());
            return null;
        }
        if (logic.isBlocking()) {
            ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.BLOCKING,
                    "blocking mode preserves AE2 one-craft semantics");
            return null;
        }

        LockCraftingMode configuredLock = logic.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE);
        if (configuredLock == LockCraftingMode.LOCK_UNTIL_RESULT
                || configuredLock == LockCraftingMode.LOCK_UNTIL_PULSE) {
            ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.LOCKED,
                    configuredLock.name());
            return null;
        }
        if (!patternDetails.supportsPushInputsToExternalInventory()) {
            ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.UNSUPPORTED_PATTERN,
                    patternDetails.getClass().getName());
            return null;
        }

        var blockEntity = access.neoecoae$getHost().getBlockEntity();
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return null;
        }

        List<Direction> activeSides = List.copyOf(access.neoecoae$invokeGetActiveSides());
        for (Direction direction : activeSides) {
            var adjacentPosition = blockEntity.getBlockPos().relative(direction);
            var adjacentSide = direction.getOpposite();
            ICraftingMachine craftingMachine = ICraftingMachine.of(level, adjacentPosition, adjacentSide);
            if (craftingMachine != null && craftingMachine.acceptsPlans()) {
                ECOProcessingBatchDiagnostics.record(
                        ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.DEDICATED_MACHINE,
                        direction.getName());
                return null;
            }
        }

        List<ECOProcessingBatchTarget> targets = new ArrayList<>();
        for (Direction direction : activeSides) {
            PatternProviderTarget target = access.neoecoae$invokeFindAdapter(direction);
            if (target != null) {
                targets.add(new ECOProcessingBatchTarget(direction, target));
            }
        }
        if (targets.isEmpty()) {
            ECOProcessingBatchDiagnostics.record(
                    ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.NO_TARGET,
                    "AE2 found no external inventory target");
            return null;
        }

        int normalizedRoundRobin = Math.floorMod(access.neoecoae$getRoundRobinIndex(), targets.size());
        long boundedRequest = Math.min(requestedCrafts, Math.max(2, NEConfig.ecoProcessingBatchMax));
        for (int offset = 0; offset < targets.size(); offset++) {
            int targetIndex = (normalizedRoundRobin + offset) % targets.size();
            ECOProcessingBatchTarget target = targets.get(targetIndex);
            ECOProcessingBatchCapacity capacity = ECOProcessingBatchCapacity.capture(
                    target, prototype, boundedRequest);
            if (capacity == null) {
                ECOProcessingBatchDiagnostics.record(
                        ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.INPUT_OVERFLOW,
                        "target=" + target.direction().getName());
                continue;
            }
            long count = Math.min(boundedRequest, capacity.maxCrafts());
            if (count < 2L) {
                continue;
            }

            int nextRoundRobin = normalizedRoundRobin + offset + 1;
            return new ECOProcessingBatchAdmission(count, prototype,
                    (committedPrototype, transferOwnership) -> commit(
                            access,
                            patternDetails,
                            committedPrototype,
                            count,
                            target,
                            nextRoundRobin,
                            transferOwnership));
        }

        ECOProcessingBatchDiagnostics.record(
                ECOProcessingBatchDiagnostics.ECOProcessingBatchFallbackReason.NO_CAPACITY,
                "requested=" + boundedRequest);
        return null;
    }

    private static boolean commit(
            ECOPatternProviderBatchAccess access,
            IPatternDetails patternDetails,
            KeyCounter[] prototype,
            long count,
            ECOProcessingBatchTarget target,
            int nextRoundRobin,
            Runnable transferOwnership) {
        List<GenericStack> expandedInputs = expandPatternInputs(patternDetails, prototype, count);
        List<GenericStack> sendList = access.neoecoae$getSendList();
        if (!sendList.isEmpty()) {
            throw new IllegalStateException("Pattern Provider received another batch while inputs were pending");
        }

        access.neoecoae$setSendDirection(target.direction());
        sendList.addAll(expandedInputs);

        // The CPU can no longer refund inputs after this callback.
        transferOwnership.run();
        if (!expandedInputs.isEmpty()) {
            access.neoecoae$alertPendingSendList();
        }
        access.neoecoae$invokeSendStacksOut();
        access.neoecoae$invokeOnPushPatternSuccess(patternDetails);
        access.neoecoae$setRoundRobinIndex(nextRoundRobin);
        return true;
    }

    /** Expands using AE2's pattern callback so sparse slots and duplicate inputs are retained. */
    static List<GenericStack> expandPatternInputs(
            IPatternDetails patternDetails,
            KeyCounter[] prototype,
            long count) {
        Objects.requireNonNull(patternDetails, "patternDetails");
        Objects.requireNonNull(prototype, "prototype");
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive");
        }

        PrototypeSnapshot snapshot = copyPrototype(prototype);
        KeyCounter emitted = new KeyCounter();
        List<GenericStack> expanded = new ArrayList<>();
        patternDetails.pushInputsToExternalInventory(snapshot.perCraft(), (what, amount) -> {
            if (what == null || amount <= 0L) {
                throw new IllegalStateException("Pattern emitted an invalid processing input");
            }
            emitted.set(what, Math.addExact(emitted.get(what), amount));
            expanded.add(new GenericStack(what, Math.multiplyExact(amount, count)));
        });

        if (!sameAmounts(snapshot.aggregate(), emitted) || !sameAmounts(emitted, snapshot.aggregate())) {
            throw new IllegalStateException("Pattern did not emit its complete input prototype");
        }
        return List.copyOf(expanded);
    }

    private static PrototypeSnapshot copyPrototype(KeyCounter[] prototype) {
        KeyCounter aggregate = new KeyCounter();
        KeyCounter[] perCraft = new KeyCounter[prototype.length];
        for (int index = 0; index < prototype.length; index++) {
            KeyCounter source = prototype[index];
            if (source == null) {
                throw new IllegalArgumentException("prototype counter at index " + index + " is null");
            }
            KeyCounter copy = new KeyCounter();
            for (var entry : source) {
                long amount = entry.getLongValue();
                if (entry.getKey() == null || amount < 0L) {
                    throw new IllegalArgumentException("prototype contains an invalid processing input");
                }
                if (amount > 0L) {
                    aggregate.set(entry.getKey(), Math.addExact(aggregate.get(entry.getKey()), amount));
                    copy.add(entry.getKey(), amount);
                }
            }
            perCraft[index] = copy;
        }
        return new PrototypeSnapshot(aggregate, perCraft);
    }

    private static boolean sameAmounts(KeyCounter expected, KeyCounter actual) {
        for (var entry : expected) {
            if (actual.get(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    private record PrototypeSnapshot(KeyCounter aggregate, KeyCounter[] perCraft) {
    }
}
