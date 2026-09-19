package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.stacks.KeyCounter;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathLookup;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOStatefulBatchCalculator;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedVirtualExecution;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Owns worker selection and the ordinary/FastPath dispatch contract for one pattern bus. */
final class ECOCraftingPatternBusDispatcher {
    private final ECOCraftingPatternBusBlockEntity host;
    /** Ordinary one-craft dispatch follows AdvancedAE's successful-target round-robin. */
    private int roundRobinIndex;

    ECOCraftingPatternBusDispatcher(ECOCraftingPatternBusBlockEntity host) {
        this.host = host;
    }

    boolean pushPattern(appeng.api.crafting.IPatternDetails patternDetails, KeyCounter[] inputHolder,
                        @Nullable UUID craftingJobId) {
        Level level = host.getLevel();
        ECOExtractedPatternExecution execution = level == null
            ? ECOExtractedPatternExecution.slow(patternDetails, inputHolder)
            : ECOExtractedPatternExecution.fromProviderPush(patternDetails, inputHolder, level);
        return pushPattern(execution, craftingJobId);
    }

    /** Ordinary single-craft fallback without another FastPath lookup. */
    boolean pushPatternSlow(appeng.api.crafting.IPatternDetails pattern,
                            KeyCounter[] inputs, @Nullable UUID craftingJobId) {
        return pushPattern(ECOExtractedPatternExecution.slow(pattern, inputs), craftingJobId);
    }

    boolean pushPattern(ECOExtractedPatternExecution execution, @Nullable UUID craftingJobId) {
        var cluster = host.getCraftingCluster();
        if (execution.molecularPattern() == null || cluster == null) {
            return false;
        }
        // Match AdvancedAE's ordinary provider routing: visit reachable workers from a rotating cursor and advance
        // only after a successful push. Batch offers use the capacity-ranked path below, but a single craft should
        // not keep concentrating on the worker with the most free slots.
        List<ECOCraftingWorkerBlockEntity> candidates = cluster.collectDispatchCandidateWorkers();
        if (candidates.isEmpty()) {
            return false;
        }
        int start = Math.floorMod(roundRobinIndex, candidates.size());
        for (int offset = 0; offset < candidates.size(); offset++) {
            int candidateIndex = (start + offset) % candidates.size();
            ECOCraftingWorkerBlockEntity worker = candidates.get(candidateIndex);
            // The worker performs the authoritative capacity check immediately before accepting ownership.
            if (worker.pushPattern(execution, craftingJobId)) {
                roundRobinIndex = (candidateIndex + 1) % candidates.size();
                return true;
            }
        }
        return false;
    }

    @Nullable
    ECOFastPathDispatchProvider.Preparation prepareFastPath(ECOBatchDispatchContext context) {
        var controller = host.getCraftingController();
        if (controller == null || context.level() != host.getLevel()) {
            return null;
        }
        var execution = context.execution();
        if (!execution.canUseFastPath()) {
            return null;
        }
        if (controller.isFullVirtualCraftingMode()) {
            var offer = findVirtualFastPathOffer(execution);
            if (offer == null || !batchRecipe(offer.recipe(), execution)) {
                return null;
            }
            var recipe = offer.recipe();
            var calculator = ECOStatefulBatchCalculator.create(recipe, execution);
            long capacity = calculator == null
                ? recipe.arithmeticBatchLimit() : calculator.arithmeticBatchLimit();
            return capacity <= 0L ? null : new ECOFastPathDispatchProvider.Preparation(
                capacity, calculator, true, batch -> {
                    var verified = recipe.withVirtualBatch(batch.craftCount(), context.craftingJobId(),
                        batch.inputTotal(), batch.outputTotal(), batch.remainingTotal());
                    return verified != null && pushVirtualBatch(verified, offer);
                });
        }

        var offer = findBatchFastPathOffer(execution, Integer.MAX_VALUE);
        if (offer == null || !batchRecipe(offer.recipe(), execution)) {
            return null;
        }
        long capacity = Math.max(0, controller.getCraftingCoolantCraftLimit(
            5, controller.getEffectiveOverclockTimes(), offer.maxBatchSize()));
        var recipe = offer.recipe();
        var calculator = ECOStatefulBatchCalculator.create(recipe, execution);
        return capacity <= 0L ? null : new ECOFastPathDispatchProvider.Preparation(
            capacity, calculator, true, batch -> {
                if (batch.craftCount() > Integer.MAX_VALUE) {
                    return false;
                }
                var verified = recipe.withBatch((int) batch.craftCount(), context.craftingJobId(),
                    batch.inputTotal(), batch.outputTotal(), batch.remainingTotal());
                return verified != null && acceptVerifiedBatch(verified, offer);
            });
    }

    @Nullable ECOFastPathDispatchProvider.ExactPreparation prepareExactFastPath(
            ECOBatchDispatchContext context, java.math.BigInteger requested) {
        var controller = host.getCraftingController();
        if (requested.signum() <= 0 || controller == null || !controller.isFullVirtualCraftingMode()
                || context.level() != host.getLevel() || context.craftingJobId() == null) return null;
        var execution = context.execution();
        if (!execution.canUseFastPath()) return null;
        var offer = findVirtualFastPathOffer(execution);
        if (offer == null || !batchRecipe(offer.recipe(), execution)
                || offer.recipe().reusableStateModel() != null
                || offer.recipe().durabilityModel() != null) return null;
        return new ECOFastPathDispatchProvider.ExactPreparation(requested, () -> {
            var cluster = host.getCraftingCluster();
            return cluster != null && cluster.isDispatchCandidate(offer.worker())
                && offer.worker().pushExactVirtualBatch(offer.recipe(), requested, context.craftingJobId());
        });
    }

    boolean acceptVerifiedBatch(ECOVerifiedFastPathExecution verified,
                                 @Nullable ECOCraftingPatternBusBlockEntity.BatchFastPathOffer offer) {
        var cluster = host.getCraftingCluster();
        if (offer == null || cluster == null) {
            return false;
        }
        // The credential must be the one minted for this offer. That single reference check replaces the value
        // comparison of three per-craft stack lists, and it also pins the batch size the offer was sized for.
        if (verified.recipe() != offer.recipe()) {
            return false;
        }
        int batchSize = verified.batchSize();
        ECOCraftingWorkerBlockEntity worker = offer.worker();
        if (offer.maxBatchSize() < batchSize
            || !cluster.isDispatchCandidate(worker)
            || worker.getAvailableThreadSlots() < batchSize
            || availableThreadSlots() < batchSize) {
            return false;
        }
        return worker.pushBatch(verified);
    }

    boolean pushVirtualBatch(ECOVerifiedVirtualExecution verified,
                             @Nullable ECOCraftingPatternBusBlockEntity.VirtualFastPathOffer offer) {
        var cluster = host.getCraftingCluster();
        if (offer == null || cluster == null || verified.recipe() != offer.recipe()) {
            return false;
        }
        ECOCraftingWorkerBlockEntity worker = offer.worker();
        return cluster.isDispatchCandidate(worker)
            && worker.getAvailableBatchCapacity() > 0
            && worker.pushVirtualBatch(verified);
    }

    @Nullable
    ECOCraftingPatternBusBlockEntity.VirtualFastPathOffer findVirtualFastPathOffer(
        ECOExtractedPatternExecution execution
    ) {
        var cluster = host.getCraftingCluster();
        ECOCraftingSystemBlockEntity controller = host.getCraftingController();
        if (cluster == null || controller == null || !controller.getCapabilitySnapshot().virtualMode()) {
            return null;
        }
        ECOFastPathLookup lookup = cluster.getFastPathCache().lookup(
            execution,
            TickHandler.instance().getCurrentTick(),
            AE2PatternIntrospection.reloadGeneration()
        );
        if (!lookup.isVerified()) {
            return null;
        }
        ECOCraftingPatternDispatch.Candidate best = findBestDispatchCandidate(cluster);
        return best == null
            ? null
            : new ECOCraftingPatternBusBlockEntity.VirtualFastPathOffer(best.worker(), lookup.recipe());
    }

    @Nullable
    ECOCraftingPatternBusBlockEntity.BatchFastPathOffer findBatchFastPathOffer(
        ECOExtractedPatternExecution execution,
        int requestedBatchSize
    ) {
        var cluster = host.getCraftingCluster();
        if (cluster == null || requestedBatchSize <= 0) {
            return null;
        }
        int globalAvailableSlots = availableThreadSlots();
        if (globalAvailableSlots <= 0) {
            return null;
        }
        // Recipe-level verification is shared knowledge, so it is resolved once for the whole search instead of
        // once per candidate worker.
        ECOFastPathLookup lookup = cluster.getFastPathCache().lookup(
            execution,
            TickHandler.instance().getCurrentTick(),
            AE2PatternIntrospection.reloadGeneration()
        );
        if (!lookup.isVerified()) {
            return null;
        }
        ECOVerifiedFastPathRecipe verifiedRecipe = lookup.recipe();
        if (verifiedRecipe == null) {
            return null;
        }
        ECOCraftingPatternDispatch.Candidate best = findBestDispatchCandidate(cluster);
        if (best == null) {
            return null;
        }
        // calculateBatchOfferSize is monotone in the worker's free slots, so the highest-ranked candidate also
        // has the largest offer. Taking it keeps a batch concentrated on one worker instead of splitting it.
        int maxBatchSize = ECOCraftingPatternBusBlockEntity.calculateBatchOfferSize(
            requestedBatchSize,
            best.availableSlots(),
            globalAvailableSlots,
            statefulDispatchLimit(verifiedRecipe, execution)
        );
        if (maxBatchSize <= 0) {
            return null;
        }
        return new ECOCraftingPatternBusBlockEntity.BatchFastPathOffer(best.worker(), verifiedRecipe, maxBatchSize);
    }

    private static boolean batchRecipe(ECOVerifiedFastPathRecipe recipe,
                                       ECOExtractedPatternExecution execution) {
        return recipe.isVerifiedFor(execution);
    }

    private static long statefulDispatchLimit(ECOVerifiedFastPathRecipe recipe,
                                              ECOExtractedPatternExecution execution) {
        var calculator = ECOStatefulBatchCalculator.create(recipe, execution);
        return calculator == null ? recipe.arithmeticBatchLimit() : calculator.arithmeticBatchLimit();
    }

    private static ECOCraftingPatternDispatch.Candidate findBestDispatchCandidate(
        cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster cluster
    ) {
        return ECOCraftingPatternDispatch.best(cluster.collectDispatchCandidateWorkers());
    }

    boolean recoverJobToNetwork(UUID craftingJobId, appeng.api.storage.MEStorage storage) {
        var cluster = host.getCraftingCluster();
        if (cluster == null) {
            return false;
        }
        // Dispatch may have crossed a Network Switch, so recovery must cover the same reachable set. Recovery is
        // idempotent per thread, so overlapping attempts from several buses are harmless.
        boolean recoveredAll = true;
        for (ECOCraftingWorkerBlockEntity worker : cluster.collectDispatchCandidateWorkers()) {
            if (!worker.recoverJobToNetwork(craftingJobId, storage)) {
                recoveredAll = false;
            }
        }
        return recoveredAll;
    }

    boolean isBusy() {
        var cluster = host.getCraftingCluster();
        return cluster == null
            || host.getCraftingController() == null
            || !cluster.hasAvailableDispatchCandidate();
    }

    int availableThreadSlots() {
        var cluster = host.getCraftingCluster();
        if (cluster == null || host.getCraftingController() == null) {
            return 0;
        }
        long available = 0L;
        for (ECOCraftingWorkerBlockEntity worker : cluster.collectDispatchCandidateWorkers()) {
            available = cn.dancingsnow.neoecoae.crafting.amount.NEMath.saturatingAdd(
                available, worker.getAvailableBatchCapacity());
        }
        return (int) Math.min(Integer.MAX_VALUE, available);
    }
}
