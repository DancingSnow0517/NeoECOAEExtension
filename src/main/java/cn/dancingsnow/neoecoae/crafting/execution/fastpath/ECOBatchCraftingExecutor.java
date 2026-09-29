package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.ECOBatchCapacityProvider;
import cn.dancingsnow.neoecoae.api.me.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.api.me.ECOStatefulBatchProvider;
import cn.dancingsnow.neoecoae.compat.gtl.GTLCraftingProviderCompat;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchEnergyLedger;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchExecutor;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchPlanner;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Plans a current provider offer; CPU task accounting follows the ownership receipt. */
public final class ECOBatchCraftingExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private ECOBatchCraftingExecutor() {}

    public static boolean canBatch(ICraftingProvider provider) {
        return provider instanceof ECOBatchCapacityProvider
                || ECOParallelCraftingProviders.find(provider) != null
                || GTLCraftingProviderCompat.isAutoExpandProvider(provider);
    }

    @Nullable public static PreparedBatch prepare(
            ICraftingProvider provider,
            IPatternDetails pattern,
            KeyCounter[] inputs,
            KeyCounter outputs,
            KeyCounter containers,
            ListCraftingInventory inventory,
            long maxCrafts,
            IEnergyService energyService,
            Level level,
            UUID craftingJobId) {
        if (provider instanceof ECOBatchCapacityProvider capacity) {
            PreparedBatch own = prepare(
                    capacity,
                    pattern,
                    inputs,
                    outputs,
                    containers,
                    inventory,
                    maxCrafts,
                    energyService,
                    level,
                    craftingJobId);
            if (own != null) return own;
        }
        var parallel = ECOParallelCraftingProviders.find(provider);
        if (parallel != null && pattern.supportsPushInputsToExternalInventory() && maxCrafts > 0L) {
            try {
                var context =
                        ECOBatchDispatchContext.create(pattern, inputs, outputs, containers, level, craftingJobId);
                if (context.execution().fastPathType() == ECORecipeClassifier.Type.NORMAL) {
                    PreparedBatch batch = prepareLinear(
                            context,
                            inventory,
                            maxCrafts,
                            Math.max(0L, parallel.eco$getAvailableParallelSlots()),
                            energyService,
                            count -> parallel.eco$pushPatternBatch(
                                    pattern, multiplyInputSlots(context, count), count, craftingJobId));
                    if (batch != null) return batch;
                }
            } catch (RuntimeException unavailable) {
                LOGGER.debug("Parallel batch preparation unavailable", unavailable);
            }
        }
        return prepareGtlAutoExpand(
                provider,
                pattern,
                inputs,
                outputs,
                containers,
                inventory,
                maxCrafts,
                energyService,
                level,
                craftingJobId);
    }

    @Nullable public static PreparedBatch prepare(
            ECOBatchCapacityProvider provider,
            IPatternDetails pattern,
            KeyCounter[] inputs,
            KeyCounter outputs,
            KeyCounter containers,
            ListCraftingInventory inventory,
            long maxCrafts,
            IEnergyService energyService,
            Level level,
            UUID craftingJobId) {
        if (maxCrafts <= 0L) return null;
        try {
            var context = ECOBatchDispatchContext.create(pattern, inputs, outputs, containers, level, craftingJobId);
            long capacity = Math.max(0L, provider.eco$getBatchCapacity(context));
            if (capacity <= 0L) return null;
            var stateful = provider instanceof ECOStatefulBatchProvider statefulProvider
                    ? statefulProvider.eco$getStatefulBatchCalculator(context)
                    : null;
            if (provider instanceof ECOStatefulBatchProvider
                    && stateful == null
                    && context.execution().fastPathType() != ECORecipeClassifier.Type.NORMAL) return null;
            if (stateful == null) {
                return prepareLinear(
                        context,
                        inventory,
                        maxCrafts,
                        capacity,
                        energyService,
                        count -> provider.eco$pushBatchAdmission(context, count));
            }
            long requested = ECOBatchPlanner.plan(
                    maxCrafts, Long.MAX_VALUE, capacity, Long.MAX_VALUE, stateful.arithmeticBatchLimit());
            requested = stateful.maxCraftsFromInventory(inventory, requested);
            double unitPower = CraftingCpuHelper.calculatePatternPower(inputs);
            long count = affordable(unitPower, requested, energyService);
            if (count <= 0L) return null;
            long fixedCount = count;
            return new PreparedBatch(
                    count,
                    stateful.batchInputs(count),
                    ECOBatchCraftingHelper.multiply(context.outputs(), count),
                    stateful.batchRemainders(count),
                    unitPower,
                    false,
                    () -> provider.eco$pushBatchAdmission(context, fixedCount));
        } catch (RuntimeException unavailable) {
            LOGGER.debug("ECO batch preparation unavailable; no resources reserved", unavailable);
            return null;
        }
    }

    @Nullable private static PreparedBatch prepareLinear(
            ECOBatchDispatchContext context,
            ListCraftingInventory inventory,
            long maxCrafts,
            long capacity,
            IEnergyService energyService,
            LongFunction<ECOBatchAdmission> dispatch) {
        long material = ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                context.inputItems(), context.outputs(), context.containerItems());
        long requested = ECOBatchPlanner.plan(maxCrafts, Long.MAX_VALUE, capacity, Long.MAX_VALUE, material);
        requested = ECOBatchCraftingHelper.maxCraftsFromInventory(inventory, context.inputItems(), requested);
        double unitPower = CraftingCpuHelper.calculatePatternPower(context.inputCounters());
        long count = affordable(unitPower, requested, energyService);
        if (count <= 0L) return null;
        return new PreparedBatch(
                count,
                ECOBatchCraftingHelper.multiply(context.inputItems(), count),
                ECOBatchCraftingHelper.multiply(context.outputs(), count),
                ECOBatchCraftingHelper.multiply(context.containerItems(), count),
                unitPower,
                true,
                () -> dispatch.apply(count));
    }

    private static long affordable(double unitPower, long requested, IEnergyService energyService) {
        return ECOBatchCraftingHelper.maxAffordableCrafts(
                unitPower,
                requested,
                amount -> energyService.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG));
    }

    /** GTL's expanded-input contract is atomic and cannot accept a prefix. */
    @Nullable public static PreparedBatch prepareGtlAutoExpand(
            ICraftingProvider provider,
            IPatternDetails pattern,
            KeyCounter[] inputs,
            KeyCounter outputs,
            KeyCounter containers,
            ListCraftingInventory inventory,
            long maxCrafts,
            IEnergyService energyService,
            Level level,
            UUID craftingJobId) {
        if (maxCrafts <= 1L
                || !pattern.supportsPushInputsToExternalInventory()
                || !GTLCraftingProviderCompat.isAutoExpandProvider(provider)) return null;
        try {
            var context = ECOBatchDispatchContext.create(pattern, inputs, outputs, containers, level, craftingJobId);
            long capacity = GTLCraftingProviderCompat.getMaxOperations(provider, pattern, maxCrafts);
            long material = ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                    context.inputItems(), context.outputs(), context.containerItems());
            long requested = ECOBatchPlanner.plan(
                    maxCrafts,
                    ECOBatchCraftingHelper.maxCraftsFromInventory(inventory, context.inputItems(), maxCrafts),
                    capacity,
                    Long.MAX_VALUE,
                    material);
            double unitPower = CraftingCpuHelper.calculatePatternPower(inputs);
            long count = affordable(unitPower, requested, energyService);
            if (count <= 1L) return null;
            KeyCounter[] expanded = multiplyInputSlots(context, count);
            return new PreparedBatch(
                    count,
                    ECOBatchCraftingHelper.multiply(context.inputItems(), count),
                    ECOBatchCraftingHelper.multiply(context.outputs(), count),
                    ECOBatchCraftingHelper.multiply(context.containerItems(), count),
                    unitPower,
                    false,
                    () -> provider.pushPattern(pattern, expanded)
                            ? ECOBatchAdmission.accepted(count, true)
                            : ECOBatchAdmission.rejected());
        } catch (RuntimeException unavailable) {
            LOGGER.debug("GTL auto-expand preparation unavailable; no resources reserved", unavailable);
            return null;
        }
    }

    private static KeyCounter[] multiplyInputSlots(ECOBatchDispatchContext context, long count) {
        KeyCounter[] result = context.inputCounters();
        for (int i = 0; i < result.length; i++) {
            KeyCounter scaled = new KeyCounter();
            for (var entry : result[i]) scaled.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), count));
            result[i] = scaled;
        }
        return result;
    }

    public static final class PreparedBatch {
        private final long craftCount;
        private final List<GenericStack> inputTotal;
        private final List<GenericStack> outputs;
        private final List<GenericStack> remainders;
        private final double unitPower;
        private final boolean linear;
        private final Supplier<ECOBatchAdmission> dispatch;
        private final AtomicBoolean submitted = new AtomicBoolean();

        private PreparedBatch(
                long craftCount,
                List<GenericStack> inputTotal,
                List<GenericStack> outputs,
                List<GenericStack> remainders,
                double unitPower,
                boolean linear,
                Supplier<ECOBatchAdmission> dispatch) {
            this.craftCount = craftCount;
            this.inputTotal = List.copyOf(inputTotal);
            this.outputs = List.copyOf(outputs);
            this.remainders = List.copyOf(remainders);
            this.unitPower = unitPower;
            this.linear = linear;
            this.dispatch = dispatch;
        }

        public long craftCount() {
            return craftCount;
        }

        public List<GenericStack> inputTotal() {
            return inputTotal;
        }

        public List<GenericStack> outputs() {
            return outputs;
        }

        public List<GenericStack> remainders() {
            return remainders;
        }

        public double power() {
            return unitPower * craftCount;
        }

        public List<GenericStack> outputsForAccepted(long accepted) {
            return scalePrefix(outputs, accepted);
        }

        public List<GenericStack> remaindersForAccepted(long accepted) {
            return scalePrefix(remainders, accepted);
        }

        private List<GenericStack> scalePrefix(List<GenericStack> totals, long accepted) {
            if (accepted == craftCount) return totals;
            if (!linear || accepted <= 0L || accepted > craftCount)
                throw new IllegalArgumentException("Invalid batch prefix");
            return totals.stream()
                    .map(stack -> {
                        if (stack.amount() % craftCount != 0L)
                            throw new IllegalStateException("Nonlinear batch output");
                        return new GenericStack(
                                stack.what(), Math.multiplyExact(stack.amount() / craftCount, accepted));
                    })
                    .toList();
        }

        public ECOBatchAdmission submit(ListCraftingInventory inventory, IEnergyService energyService) {
            return submit(inventory, energyService, new ECOBatchEnergyLedger());
        }

        public ECOBatchAdmission submit(
                ListCraftingInventory inventory, IEnergyService energyService, ECOBatchEnergyLedger ledger) {
            if (!submitted.compareAndSet(false, true)) throw new IllegalStateException("Batch already submitted");
            var energy = ledger.reserve(energyService, unitPower, java.math.BigInteger.valueOf(craftCount));
            return ECOBatchExecutor.execute(
                    inventory, inputTotal, java.util.Map.of(), craftCount, linear, energy, dispatch);
        }
    }
}
