package cn.dancingsnow.neoecoae.impl.crafting.fastpath.external;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchEnergyReservation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Batches processing patterns directly into GTLCore's unlimited pattern buffers. */
public final class GTLCorePatternBufferDispatcher {
    private static final String PATTERN_BUFFER_BASE_CLASS =
            "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase";
    private static final String INTEGRATED_CIRCUIT_ID = "gtceu:integrated_circuit";
    private static final Class<?> PATTERN_BUFFER_TYPE = loadPatternBufferType();

    private GTLCorePatternBufferDispatcher() {}

    public static long dispatch(
            Iterable<ICraftingProvider> providers,
            IPatternDetails details,
            long requestedOperations,
            Level level,
            IEnergyService energyService,
            BatchTarget target) {
        if (PATTERN_BUFFER_TYPE == null
                || !details.supportsPushInputsToExternalInventory()
                || requestedOperations < 2L) {
            return 0L;
        }

        List<ICraftingProvider> patternBuffers = findPatternBuffers(providers);
        if (patternBuffers.isEmpty()) {
            return 0L;
        }

        KeyCounter expectedOutputs = new KeyCounter();
        KeyCounter expectedContainerItems = new KeyCounter();
        KeyCounter[] firstInputs = CraftingCpuHelper.extractPatternInputs(
                details, target.inventory(), level, expectedOutputs, expectedContainerItems);
        if (firstInputs == null) {
            return 0L;
        }

        boolean firstInputsOwned = true;
        boolean extraInputsOwned = false;
        ECOBatchEnergyReservation energyReservation = null;
        BatchPlan plan = null;
        try {
            plan = planBatch(
                    target.inventory(), firstInputs, expectedOutputs, expectedContainerItems, requestedOperations);
            if (plan.batchSize() < 2L) {
                return 0L;
            }

            if (!plan.extraInputs().isEmpty()) {
                ECOBatchCraftingHelper.extractExact(target.inventory(), plan.extraInputs());
                extraInputsOwned = true;
            }

            double totalPower = CraftingCpuHelper.calculatePatternPower(plan.batchInputs());
            double dispatchPower = totalPower / plan.batchSize() * target.networkPowerMultiplier();
            energyReservation = ECOBatchEnergyReservation.tryReserve(energyService, dispatchPower, false);
            if (energyReservation == null) {
                return 0L;
            }

            boolean accepted = false;
            for (ICraftingProvider provider : patternBuffers) {
                if (provider.pushPattern(details, plan.batchInputs())) {
                    accepted = true;
                    break;
                }
            }
            if (!accepted) {
                return 0L;
            }

            firstInputsOwned = false;
            extraInputsOwned = false;
            energyReservation.commit();
            energyReservation = null;

            target.consume(plan.batchSize());
            insertScaled(target.waitingFor(), expectedOutputs, plan.batchSize());
            insertScaled(target.waitingFor(), expectedContainerItems, plan.batchSize());
            for (var entry : expectedContainerItems) {
                long amount = saturatingMultiply(entry.getLongValue(), plan.batchSize());
                if (amount > 0L) {
                    target.addContainerMaxItems(amount, entry.getKey().getType());
                }
            }
            target.markDirty();
            return plan.batchSize();
        } catch (RuntimeException e) {
            AELog.warn("NeoECO GTLCore pattern buffer dispatch failed; restoring CPU resources. %s", e);
            return 0L;
        } finally {
            if (energyReservation != null) {
                RuntimeException refundFailure = energyReservation.refundSafely();
                if (refundFailure != null) {
                    AELog.warn("NeoECO GTLCore pattern buffer energy refund failed. %s", refundFailure);
                }
            }
            if (extraInputsOwned && plan != null) {
                ECOBatchCraftingHelper.insertAll(target.inventory(), plan.extraInputs());
            }
            if (firstInputsOwned) {
                CraftingCpuHelper.reinjectPatternInputs(target.inventory(), firstInputs);
            }
        }
    }

    public interface BatchTarget {
        ListCraftingInventory inventory();

        ListCraftingInventory waitingFor();

        void consume(long operations);

        void addContainerMaxItems(long amount, AEKeyType keyType);

        default double networkPowerMultiplier() {
            return 1.0D;
        }

        void markDirty();
    }

    private static BatchPlan planBatch(
            ListCraftingInventory inventory,
            KeyCounter[] firstInputs,
            KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems,
            long requestedOperations) {
        KeyCounter perCraftInputs = new KeyCounter();
        for (KeyCounter input : firstInputs) {
            for (var entry : input) {
                if (entry.getLongValue() > 0L && !isIntegratedCircuit(entry.getKey())) {
                    perCraftInputs.add(entry.getKey(), entry.getLongValue());
                }
            }
        }

        long batchSize = Math.min(requestedOperations, maxAggregateBatch(expectedOutputs, expectedContainerItems));
        for (var entry : perCraftInputs) {
            long perCraft = entry.getLongValue();
            long availableBefore = saturatingAdd(inventory.list.get(entry.getKey()), perCraft);
            batchSize = Math.min(batchSize, availableBefore / perCraft);
        }
        if (batchSize < 2L) {
            return new BatchPlan(0L, new KeyCounter[0], List.of());
        }

        KeyCounter[] batchInputs = new KeyCounter[firstInputs.length];
        for (int index = 0; index < firstInputs.length; index++) {
            KeyCounter batchInput = batchInputs[index] = new KeyCounter();
            for (var entry : firstInputs[index]) {
                long multiplier = isIntegratedCircuit(entry.getKey()) ? 1L : batchSize;
                batchInput.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
            }
        }

        List<GenericStack> extraInputs = new ArrayList<>();
        for (var entry : perCraftInputs) {
            long amount = Math.multiplyExact(entry.getLongValue(), batchSize - 1L);
            if (amount > 0L) {
                extraInputs.add(new GenericStack(entry.getKey(), amount));
            }
        }
        return new BatchPlan(batchSize, batchInputs, extraInputs);
    }

    private static long maxAggregateBatch(KeyCounter... counters) {
        KeyCounter aggregate = new KeyCounter();
        for (KeyCounter counter : counters) {
            for (var entry : counter) {
                if (entry.getLongValue() > 0L) {
                    aggregate.add(entry.getKey(), entry.getLongValue());
                }
            }
        }
        long result = Long.MAX_VALUE;
        for (var entry : aggregate) {
            result = Math.min(result, Long.MAX_VALUE / entry.getLongValue());
        }
        return result;
    }

    private static void insertScaled(ListCraftingInventory inventory, KeyCounter counter, long multiplier) {
        for (var entry : counter) {
            long amount = saturatingMultiply(entry.getLongValue(), multiplier);
            if (amount > 0L) {
                inventory.insert(entry.getKey(), amount, Actionable.MODULATE);
            }
        }
    }

    private static List<ICraftingProvider> findPatternBuffers(Iterable<ICraftingProvider> providers) {
        List<ICraftingProvider> result = new ArrayList<>();
        Set<ICraftingProvider> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ICraftingProvider provider : providers) {
            if (PATTERN_BUFFER_TYPE.isInstance(provider) && seen.add(provider)) {
                result.add(provider);
            }
        }
        return result;
    }

    private static boolean isIntegratedCircuit(AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) {
            return false;
        }
        ResourceLocation id = itemKey.getReadOnlyStack()
                .getItem()
                .builtInRegistryHolder()
                .key()
                .location();
        return INTEGRATED_CIRCUIT_ID.equals(id.toString());
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        return left <= 0L || right <= 0L ? 0L : left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    @Nullable private static Class<?> loadPatternBufferType() {
        try {
            return Class.forName(
                    PATTERN_BUFFER_BASE_CLASS, false, GTLCorePatternBufferDispatcher.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    private record BatchPlan(long batchSize, KeyCounter[] batchInputs, List<GenericStack> extraInputs) {}
}
