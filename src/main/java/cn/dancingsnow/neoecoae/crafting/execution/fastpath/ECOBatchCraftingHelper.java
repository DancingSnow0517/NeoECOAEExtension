package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntFunction;
import java.util.function.LongFunction;

public final class ECOBatchCraftingHelper {
    /** Entry limit for verified and persisted fast-path host contracts, not general provider dispatch. */
    public static final int MAX_BATCH_STACK_ENTRIES = 64;
    /**
     * Per-entry amount limit for a multiplied batch total, and therefore the only hard ceiling a batch
     * has. How many crafts a batch may carry is decided by the live capability of the F-series host that
     * accepts it, so no fixed batch-size constant exists.
     */
    public static final long MAX_BATCH_STACK_AMOUNT = Long.MAX_VALUE;

    private ECOBatchCraftingHelper() {
    }

    /**
     * Sanitizes a persisted batch size. Only the lower bound is structural; the plausible upper bound
     * depends on the batch's own totals and is applied by the caller that owns them.
     */
    public static int clampPersistedBatchSize(int batchSize) {
        return Math.max(1, batchSize);
    }

    public static void validateBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }

    /**
     * Largest batch multiplier that keeps a single per-craft entry within {@link #MAX_BATCH_STACK_AMOUNT}
     * once it is multiplied out.
     */
    public static long maxBatchSizeForAmount(long perCraftAmount) {
        if (perCraftAmount <= 0L) {
            return 0;
        }
        return MAX_BATCH_STACK_AMOUNT / perCraftAmount;
    }

    /**
     * Largest batch multiplier that keeps every per-craft entry of a recipe within
     * {@link #MAX_BATCH_STACK_AMOUNT}. This replaces a fixed batch cap: the bound follows from the recipe
     * itself, so a host may batch as many crafts as its own thread capacity allows.
     */
    public static long maxBatchSizeForPerCraftStacks(
        List<GenericStack> inputsPerCraft,
        List<GenericStack> outputsPerCraft,
        List<GenericStack> remainingPerCraft
    ) {
        long max = Long.MAX_VALUE;
        max = Math.min(max, maxBatchSizeForStacks(inputsPerCraft));
        max = Math.min(max, maxBatchSizeForStacks(outputsPerCraft));
        max = Math.min(max, maxBatchSizeForStacks(remainingPerCraft));
        var waitingPerCraft = new ArrayList<>(outputsPerCraft);
        waitingPerCraft.addAll(remainingPerCraft);
        max = Math.min(max, maxBatchSizeForStacks(waitingPerCraft));
        return max;
    }

    /**
     * Upper bound implied by an already-multiplied batch total: every craft contributes at least one unit
     * to each entry, so the batch size can never exceed the smallest total amount. Used to reject a
     * corrupted persisted thread-slot count without inventing a magic limit.
     */
    public static int maxBatchSizeFromTotals(List<GenericStack> totals) {
        int max = Integer.MAX_VALUE;
        for (GenericStack stack : totals) {
            if (stack == null) {
                continue;
            }
            max = (int) Math.min(max, Math.max(0L, stack.amount()));
        }
        return max;
    }

    private static long maxBatchSizeForStacks(List<GenericStack> perCraft) {
        Object2LongOpenHashMap<AEKey> totals = new Object2LongOpenHashMap<>();
        try {
            for (GenericStack stack : perCraft) {
                if (stack == null || stack.amount() <= 0L) return 0L;
                totals.put(stack.what(), Math.addExact(totals.getLong(stack.what()), stack.amount()));
            }
        } catch (ArithmeticException overflow) {
            return 0L;
        }
        long max = Long.MAX_VALUE;
        for (var amounts = totals.values().iterator(); amounts.hasNext();) {
            long amount = amounts.nextLong();
            max = Math.min(max, maxBatchSizeForAmount(amount));
            if (max <= 0) {
                return 0;
            }
        }
        return max;
    }

    public static List<GenericStack> multiply(List<GenericStack> stacks, int multiplier) {
        return multiply(stacks, (long) multiplier);
    }

    /** Long-count multiplication used by explicit virtual batches and opt-in unknown-capacity batch commits. */
    public static List<GenericStack> multiply(List<GenericStack> stacks, long multiplier) {
        if (multiplier <= 0 || stacks.isEmpty()) {
            return List.of();
        }
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0L) throw new IllegalArgumentException("Invalid batch amount");
            long amount = multiplyExact(stack.amount(), multiplier);
            counter.set(stack.what(), Math.addExact(counter.get(stack.what()), amount));
        }
        return ECOFastPathStacks.copyCounter(counter);
    }

    /** Returns {@code total - alreadyOwned}, preserving concrete AEItemKey components. */
    public static List<GenericStack> subtract(List<GenericStack> total, List<GenericStack> alreadyOwned) {
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : total) counter.add(stack.what(), stack.amount());
        for (GenericStack stack : alreadyOwned) {
            long available = counter.get(stack.what());
            if (available < stack.amount()) {
                throw new IllegalArgumentException("Batch total is smaller than its first craft inputs");
            }
            counter.remove(stack.what(), stack.amount());
        }
        return ECOFastPathStacks.copyCounter(counter);
    }

    public static int maxCraftsFromInventory(ListCraftingInventory inventory, List<GenericStack> perCraft,
            int requested) {
        int max = requested;
        for (GenericStack stack : perCraft) {
            if (stack.amount() <= 0) {
                return 0;
            }
            // The CPU inventory is already an in-memory KeyCounter. Reading it directly avoids one
            // simulated crafting-inventory transaction per ingredient while preserving the exact
            // same concrete-input semantics as the verified fast-path key.
            long available = inventory.list.get(stack.what());
            max = Math.min(max, (int) Math.min(Integer.MAX_VALUE, available / stack.amount()));
            if (max <= 0) {
                return 0;
            }
        }
        return max;
    }

    public static long maxCraftsFromInventory(ListCraftingInventory inventory, List<GenericStack> perCraft,
            long requested) {
        long max = Math.max(0L, requested);
        for (GenericStack stack : perCraft) {
            if (stack.amount() <= 0L) {
                return 0L;
            }
            max = Math.min(max, inventory.list.get(stack.what()) / stack.amount());
            if (max <= 0L) {
                return 0L;
            }
        }
        return max;
    }

    /**
     * Finds the largest total batch whose inputs beyond the already-extracted first craft are available.
     * Stateful recipes cannot use a per-craft division here: a reusable tool is owned by the first craft and
     * contributes no additional input until a later batch starts with another concrete tool state.
     */
    public static int maxBatchSizeFromAdditionalInputs(
        ListCraftingInventory inventory,
        int requestedBatchSize,
        IntFunction<List<GenericStack>> additionalInputs
    ) {
        return maxBatchSizeFromInputFunction(inventory, requestedBatchSize, additionalInputs);
    }

    /**
     * Finds the largest complete batch whose full physical inputs are currently available. This is used after
     * the first craft of a logical task has already been dispatched; unlike additionalInputs, the function must
     * include reusable state inputs and every ordinary input for the whole next batch.
     */
    public static long maxBatchSizeFromBatchInputs(
        ListCraftingInventory inventory,
        long requestedBatchSize,
        LongFunction<List<GenericStack>> batchInputs
    ) {
        return maxBatchSizeFromInputFunction(inventory, requestedBatchSize, batchInputs);
    }

    private static int maxBatchSizeFromInputFunction(
        ListCraftingInventory inventory,
        int requestedBatchSize,
        IntFunction<List<GenericStack>> inputFunction
    ) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(inputFunction, "inputFunction");
        if (requestedBatchSize <= 1) return Math.max(0, requestedBatchSize);
        if (containsAll(inventory, inputFunction.apply(requestedBatchSize))) return requestedBatchSize;

        int low = 1;
        int high = requestedBatchSize - 1;
        while (low < high) {
            int difference = high - low;
            int candidate = low + difference / 2 + difference % 2;
            if (containsAll(inventory, inputFunction.apply(candidate))) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        return low;
    }

    public static long maxBatchSizeFromAdditionalInputs(
        ListCraftingInventory inventory,
        long requestedBatchSize,
        LongFunction<List<GenericStack>> additionalInputs
    ) {
        return maxBatchSizeFromInputFunction(inventory, requestedBatchSize, additionalInputs);
    }

    private static long maxBatchSizeFromInputFunction(
        ListCraftingInventory inventory,
        long requestedBatchSize,
        LongFunction<List<GenericStack>> inputFunction
    ) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(inputFunction, "inputFunction");
        if (requestedBatchSize <= 1L) return Math.max(0L, requestedBatchSize);
        if (containsAll(inventory, inputFunction.apply(requestedBatchSize))) return requestedBatchSize;

        long low = 1L;
        long high = requestedBatchSize - 1L;
        while (low < high) {
            long difference = high - low;
            long candidate = low + difference / 2L + difference % 2L;
            if (containsAll(inventory, inputFunction.apply(candidate))) {
                low = candidate;
            } else {
                high = candidate - 1L;
            }
        }
        return low;
    }

    private static boolean containsAll(ListCraftingInventory inventory, List<GenericStack> required) {
        for (GenericStack stack : required) {
            if (stack == null || stack.amount() <= 0L || inventory.list.get(stack.what()) < stack.amount()) {
                return false;
            }
        }
        return true;
    }

    public static int maxAffordableCrafts(
        double patternPower,
        int requested,
        DoubleUnaryOperator simulatedExtraction
    ) {
        return (int) maxAffordableCrafts(patternPower, (long) requested, simulatedExtraction);
    }

    public static long maxAffordableCrafts(
        double patternPower,
        long requested,
        DoubleUnaryOperator simulatedExtraction
    ) {
        Objects.requireNonNull(simulatedExtraction, "simulatedExtraction");
        long boundedRequested = Math.min(Math.max(0L, requested), maxEnergySafeCrafts(patternPower));
        if (boundedRequested <= 0 || !Double.isFinite(patternPower) || patternPower < 0.0D) {
            return 0;
        }
        if (patternPower == 0.0D) {
            return boundedRequested;
        }
        if (hasEnoughEnergy(patternPower, boundedRequested, simulatedExtraction)) {
            return boundedRequested;
        }

        long low = 0L;
        long high = boundedRequested - 1L;
        while (low < high) {
            long difference = high - low;
            long batchSize = low + difference / 2L + difference % 2L;
            if (hasEnoughEnergy(patternPower, batchSize, simulatedExtraction)) {
                low = batchSize;
            } else {
                high = batchSize - 1;
            }
        }
        return low;
    }

    /** Exact orders use the same rounded-up request and affordability checks as ordinary batches. */
    public static BigInteger maxAffordableCrafts(double patternPower, BigInteger requested,
            DoubleUnaryOperator simulatedExtraction) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(simulatedExtraction, "simulatedExtraction");
        if (requested.signum() <= 0 || !Double.isFinite(patternPower) || patternPower < 0) return BigInteger.ZERO;
        if (patternPower == 0) return requested;
        if (requested.bitLength() < 64) {
            return BigInteger.valueOf(maxAffordableCrafts(patternPower, requested.longValueExact(), simulatedExtraction));
        }
        BigInteger bounded = requested.min(new BigDecimal(Double.MAX_VALUE)
            .divideToIntegralValue(new BigDecimal(patternPower)).toBigInteger());
        if (bounded.signum() <= 0) return BigInteger.ZERO;
        if (hasEnoughEnergy(patternPower, bounded, simulatedExtraction)) return bounded;
        BigInteger low = BigInteger.ZERO;
        BigInteger high = bounded.subtract(BigInteger.ONE);
        while (low.compareTo(high) < 0) {
            BigInteger count = low.add(high.subtract(low).add(BigInteger.ONE).shiftRight(1));
            if (hasEnoughEnergy(patternPower, count, simulatedExtraction)) low = count;
            else high = count.subtract(BigInteger.ONE);
        }
        return low;
    }

    /**
     * Atomically extracts the requested concrete inputs.
     *
     * <p>A mismatch is an expected preview-to-commit race and is returned to the caller after every
     * physically extracted item has been restored. Unexpected inventory failures are also rolled back,
     * but remain exceptions so callers do not mistake implementation faults for ordinary contention.</p>
     */
    public static boolean extractExact(ListCraftingInventory inventory, List<GenericStack> stacks) {
        List<GenericStack> extractedStacks = new ArrayList<>(stacks.size());
        try {
            for (GenericStack stack : stacks) {
                long extracted = inventory.extract(stack.what(), stack.amount(), Actionable.MODULATE);
                if (extracted > 0L) {
                    extractedStacks.add(new GenericStack(stack.what(), extracted));
                }
                if (extracted != stack.amount()) {
                    insertAll(inventory, extractedStacks);
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            insertAll(inventory, extractedStacks);
            throw e;
        }
    }

    public static void insertAll(ListCraftingInventory inventory, List<GenericStack> stacks) {
        // ListCraftingInventory 是 CPU 的本地记账库存；向其插入是内存级别的回滚操作，
        // 预期不会像网络存储那样拒绝物品。
        for (GenericStack stack : stacks) {
            inventory.insert(stack.what(), stack.amount(), Actionable.MODULATE);
        }
    }

    private static long multiplyExact(long amount, long multiplier) {
        try {
            return Math.multiplyExact(amount, (long) multiplier);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Batch fast path amount overflow", e);
        }
    }

    private static boolean hasEnoughEnergy(
        double patternPower,
        long batchSize,
        DoubleUnaryOperator simulatedExtraction
    ) {
        return hasEnoughEnergy(patternPower, BigInteger.valueOf(batchSize), simulatedExtraction);
    }

    private static boolean hasEnoughEnergy(double patternPower, BigInteger batchSize,
            DoubleUnaryOperator simulatedExtraction) {
        double totalPower = energyRequest(patternPower, batchSize);
        if (!Double.isFinite(totalPower) || totalPower < 0.0D) {
            return false;
        }
        double extracted = simulatedExtraction.applyAsDouble(totalPower);
        if (!Double.isFinite(extracted) || extracted <= 0) return false;
        if (extracted >= totalPower) return true;
        double shortfall = totalPower - extracted;
        if (shortfall > energyTolerance(totalPower)) return false;
        if (shortfall <= patternPower * 0.5) return true;
        // One ULP may span several crafts at huge counts. Probe the next representable request to
        // distinguish multiplier rounding from a real network limit without funding missing copies.
        double probe = Math.nextUp(totalPower);
        if (!Double.isFinite(probe)) return false;
        double confirmed = simulatedExtraction.applyAsDouble(probe);
        return Double.isFinite(confirmed) && confirmed >= totalPower;
    }

    /** AE2's 0.01 AE allowance, widened relatively for large multiplier round trips. */
    public static double energyTolerance(double request) {
        return Math.max(0.01, Math.abs(request) * 1.0e-12);
    }

    /** Bounds only the AE2 interface's finite range; integer precision is handled by the energy ledger. */
    public static long maxEnergySafeCrafts(double perCraftEnergy) {
        if (!Double.isFinite(perCraftEnergy) || perCraftEnergy < 0.0D) return 0L;
        if (perCraftEnergy <= Double.MAX_VALUE / 0x1.0p63) return Long.MAX_VALUE;
        return new BigDecimal(Double.MAX_VALUE)
            .divideToIntegralValue(new BigDecimal(perCraftEnergy))
            .min(BigDecimal.valueOf(Long.MAX_VALUE)).longValueExact();
    }

    /** Preserve the supplied double's exact value and the integer count before crossing the AE2 boundary. */
    public static BigDecimal exactEnergy(double perCraftEnergy, long count) {
        return exactEnergy(perCraftEnergy, BigInteger.valueOf(count));
    }

    public static BigDecimal exactEnergy(double perCraftEnergy, BigInteger count) {
        if (!Double.isFinite(perCraftEnergy) || perCraftEnergy < 0 || count.signum() < 0) {
            throw new IllegalArgumentException("Invalid crafting energy");
        }
        return new BigDecimal(perCraftEnergy).multiply(new BigDecimal(count));
    }

    public static double energyRequest(double perCraftEnergy, long count) {
        return energyRequest(perCraftEnergy, BigInteger.valueOf(count));
    }

    public static double energyRequest(double perCraftEnergy, BigInteger count) {
        var exact = exactEnergy(perCraftEnergy, count);
        double request = exact.doubleValue();
        if (Double.isFinite(request) && new BigDecimal(request).compareTo(exact) < 0) {
            request = Math.nextUp(request);
        }
        return request;
    }
}
