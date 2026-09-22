package cn.dancingsnow.neoecoae.crafting.execution.batch;

import cn.dancingsnow.neoecoae.crafting.execution.fastpath.*;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;

import java.util.List;
import java.util.Map;
import java.math.BigInteger;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicBoolean;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Prepares a batch for the selected provider; job accounting remains owned by the CPU. */
public final class ECOStatefulBatchPlanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private ECOStatefulBatchPlanner() {}

    @Nullable
    public static PreparedBatch prepare(
            ECOFastPathDispatchProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter outputs, KeyCounter containers,
            ListCraftingInventory inventory, long maxCrafts, double power, IEnergyService energyService,
            Level level, UUID craftingJobId) {
        return prepare(provider, pattern, inputs, outputs, containers, inventory, maxCrafts, power,
            energyService, level, craftingJobId, false);
    }

    @Nullable
    public static PreparedBatch prepare(
            ECOFastPathDispatchProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter outputs, KeyCounter containers,
            ListCraftingInventory inventory, long maxCrafts, double power, IEnergyService energyService,
            Level level, UUID craftingJobId, boolean exactOrder) {
        if (maxCrafts <= 0) return null;
        try {
            var context = ECOBatchDispatchContext.create(pattern, inputs, outputs, containers, level, craftingJobId);
            var preparation = provider.eco$prepareFastPath(context);
            if (preparation == null) return null;
            long capacity = preparation.capacity();
            var perCopy = context.inputItems();
            var statefulCalculator = preparation.statefulCalculator();
            if (preparation.statefulCalculatorRequired()
                    && statefulCalculator == null
                    && context.execution().fastPathType() != ECORecipeClassifier.Type.NORMAL) {
                return null;
            }
            boolean exactInputs = exactOrder && inventory instanceof ECOExactInventory exact && exact.isEnabled()
                && preparation.supportsExactInputs() && statefulCalculator == null;
            long materialLimit = statefulCalculator == null
                ? ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                    exactInputs ? List.of() : perCopy, context.outputs(), context.containerItems())
                : statefulCalculator.arithmeticBatchLimit();
            var identity = ECOPatternIdentity.of(pattern, provider);
            var mode = statefulCalculator == null ? ECOBatchMode.LINEAR : ECOBatchMode.STATEFUL_FAST_PATH;
            var initialPlan = new ECOBatchPlanner().plan(new ECOBatchPlanRequest(identity,
                    maxCrafts, Long.MAX_VALUE, capacity, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                    materialLimit, Long.MAX_VALUE, materialLimit, mode));
            long requested = initialPlan == null ? 0L : initialPlan.craftCount();
            if (requested <= 0) return null;
            ECOStatefulBatchCalculator.BatchContract statefulBatch = null;
            if (statefulCalculator == null) {
                if (exactInputs) {
                    for (var input : perCopy) requested = ECOExactInventory.amount(inventory, input.what())
                        .divide(BigInteger.valueOf(input.amount())).min(BigInteger.valueOf(requested)).longValueExact();
                } else {
                    requested = ECOBatchCraftingHelper.maxCraftsFromInventory(inventory, perCopy, requested);
                }
            } else {
                statefulBatch = statefulCalculator.prepareBatch(inventory, requested);
                requested = statefulBatch == null ? 0L : statefulBatch.craftCount();
            }
            long size = ECOBatchCraftingHelper.maxAffordableCrafts(power, requested,
                amount -> energyService.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG));
            var plan = new ECOBatchPlanner().plan(new ECOBatchPlanRequest(identity,
                    maxCrafts, requested, capacity, size, Long.MAX_VALUE, Long.MAX_VALUE,
                    materialLimit, Long.MAX_VALUE, requested, mode));
            if (plan == null) return null;
            size = plan.craftCount();
            if (statefulCalculator != null && size != requested) {
                statefulBatch = statefulCalculator.prepareBatch(inventory, size);
                if (statefulBatch == null || statefulBatch.craftCount() != size) return null;
            }
            var inputTotal = exactInputs ? List.<GenericStack>of() : statefulCalculator == null
                ? ECOBatchCraftingHelper.multiply(perCopy, size) : statefulBatch.inputs();
            var remainderTotal = statefulCalculator == null
                ? ECOBatchCraftingHelper.multiply(context.containerItems(), size)
                : statefulBatch.remainders();
            Map<AEKey, BigInteger> exactTotal = exactInputs ? ECOExactInventory.totals(perCopy, size) : Map.of();
            var batch = new ECOFastPathDispatchProvider.Batch(size, inputTotal,
                ECOBatchCraftingHelper.multiply(context.outputs(), size), remainderTotal, exactTotal);
            return new PreparedBatch(batch.craftCount(), inputTotal, batch.outputTotal(),
                batch.remainingTotal(), ECOBatchCraftingHelper.energyRequest(power, size), () -> preparation.push(batch),
                exactInputs ? exactTotal : Map.of());
        } catch (RuntimeException unavailable) {
            LOGGER.debug("ECO batch preparation unavailable; no inputs extracted", unavailable);
            return null;
        }
    }

    public record PreparedBatch(long craftCount, List<GenericStack> inputTotal,
            List<GenericStack> outputs, List<GenericStack> remainders,
            double power, BooleanSupplier dispatch, Map<AEKey, BigInteger> exactInputs) {
        public PreparedBatch(long count, List<GenericStack> inputs, List<GenericStack> outputs,
                List<GenericStack> remainders, double power, BooleanSupplier dispatch) {
            this(count, inputs, outputs, remainders, power, dispatch, Map.of());
        }

        public Map<AEKey, BigInteger> exactInputTotal() {
            return exactInputs.isEmpty() ? ECOExactInventory.totals(inputTotal, 1) : exactInputs;
        }

        @Override public List<GenericStack> inputTotal() {
            return exactInputs.isEmpty() ? inputTotal : exactInputs.entrySet().stream()
                .map(entry -> new GenericStack(entry.getKey(), entry.getValue().longValueExact())).toList();
        }
        public PreparedBatch {
            exactInputs = Map.copyOf(exactInputs);
            inputTotal = List.copyOf(inputTotal);
            outputs = List.copyOf(outputs);
            remainders = List.copyOf(remainders);
            BooleanSupplier target = dispatch;
            AtomicBoolean submitted = new AtomicBoolean();
            dispatch = () -> {
                if (!submitted.compareAndSet(false, true)) {
                    throw new IllegalStateException("Batch already submitted");
                }
                return target.getAsBoolean();
            };
        }

        /** All physical ownership and rollback are handled by the shared batch executor. */
        public boolean submit(ListCraftingInventory inventory, ECOFastPathFacade.Reservation energy) {
            return ECOBatchExecutor.executePrepared(inventory, inputTotal, exactInputs, energy,
                    dispatch::getAsBoolean);
        }
    }
}
