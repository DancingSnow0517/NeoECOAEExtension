package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGrid;
import appeng.api.stacks.KeyCounter;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.integration.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathLookup;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOStatefulBatchCalculator;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import com.google.common.collect.MapMaker;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/** Owns worker selection and the ordinary/FastPath dispatch contract for one pattern bus. */
final class ECOCraftingPatternBusDispatcher {
    private final ECOCraftingPatternBusBlockEntity host;
    /** Ordinary one-craft dispatch follows AdvancedAE's successful-target round-robin. */
    private int roundRobinIndex;
    private final Map<Object, OfferScope> issuedOffers = new MapMaker().weakKeys().makeMap();

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
        if (execution.molecularPattern() == null || currentController() == null || cluster == null) {
            return false;
        }
        // Match AdvancedAE's ordinary provider routing: visit reachable workers from a rotating cursor and advance
        // only after a successful push. Batch offers use the capacity-ranked path below, but a single craft should
        // not keep concentrating on the worker with the most free slots.
        var candidates = cluster.getDispatchCandidates();
        if (candidates.size() == 0) return false;
        int start = Math.floorMod(roundRobinIndex, candidates.size());
        int from = start;
        int end = candidates.size();
        while (true) {
            var candidate = candidates.next(from);
            if (candidate == null || candidate.index() >= end) {
                if (end == start || start == 0) return false;
                from = 0;
                end = start;
                continue;
            }
            var worker = candidate.worker();
            if (cluster.isDispatchCandidate(worker)
                    && host.recordProcessingTarget(worker, worker.pushPattern(execution, craftingJobId))) {
                roundRobinIndex = (candidate.index() + 1) % candidates.size();
                return true;
            }
            if (host.getCraftingCluster() != cluster || cluster.getDispatchCandidates() != candidates) return false;
            from = candidate.index() + 1;
        }
    }

    @Nullable
    ECOFastPathDispatchProvider.Preparation prepareFastPath(ECOBatchDispatchContext context) {
        var controller = currentController();
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
                capacity, calculator, !recipe.isStatelessWithoutRemainders(), batch -> {
                    if (batch.craftCount() > capacity) return false;
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
            capacity, calculator, !recipe.isStatelessWithoutRemainders(), batch -> {
                if (batch.craftCount() > capacity) {
                    return false;
                }
                var verified = recipe.withBatch((int) batch.craftCount(), context.craftingJobId(),
                    batch.inputTotal(), batch.outputTotal(), batch.remainingTotal());
                return verified != null && acceptVerifiedBatch(verified, offer);
            });
    }

    @Nullable ECOFastPathDispatchProvider.ExactPreparation prepareExactFastPath(
            ECOBatchDispatchContext context, java.math.BigInteger requested) {
        var controller = currentController();
        if (requested.signum() <= 0 || controller == null || !controller.isFullVirtualCraftingMode()
                || context.level() != host.getLevel() || context.craftingJobId() == null) return null;
        var execution = context.execution();
        if (!execution.canUseFastPath()) return null;
        var offer = findVirtualFastPathOffer(execution);
        if (offer == null || !batchRecipe(offer.recipe(), execution)
                || offer.recipe().reusableStateModel() != null
                || offer.recipe().durabilityModel() != null) return null;
        return new ECOFastPathDispatchProvider.ExactPreparation(requested, () -> {
            return validatesOffer(offer, offer.worker(), offer.recipe(), true)
                && offer.worker().getAvailableBatchCapacity() > 0
                && host.recordProcessingTarget(offer.worker(),
                    offer.worker().pushExactVirtualBatch(offer.recipe(), requested, context.craftingJobId()));
        });
    }

    boolean acceptVerifiedBatch(ECOVerifiedFastPathExecution verified,
                                 @Nullable ECOCraftingPatternBusBlockEntity.BatchFastPathOffer offer) {
        // The credential must be the one minted for this offer. That single reference check replaces the value
        // comparison of three per-craft stack lists, and it also pins the batch size the offer was sized for.
        if (offer == null || verified.recipe() != offer.recipe()
                || !validatesOffer(offer, offer.worker(), offer.recipe(), false)) {
            return false;
        }
        int batchSize = verified.batchSize();
        ECOCraftingWorkerBlockEntity worker = offer.worker();
        if (offer.maxBatchSize() < batchSize || worker.getAvailableBatchCapacity() < batchSize) {
            return false;
        }
        return host.recordProcessingTarget(worker, worker.pushBatch(verified));
    }

    boolean pushVirtualBatch(ECOVerifiedVirtualExecution verified,
                             @Nullable ECOCraftingPatternBusBlockEntity.VirtualFastPathOffer offer) {
        if (offer == null || verified.recipe() != offer.recipe()
                || !validatesOffer(offer, offer.worker(), offer.recipe(), true)) {
            return false;
        }
        ECOCraftingWorkerBlockEntity worker = offer.worker();
        return worker.getAvailableBatchCapacity() > 0
            && host.recordProcessingTarget(worker, worker.pushVirtualBatch(verified));
    }

    private @Nullable ECOCraftingSystemBlockEntity currentController() {
        var cluster = host.getCraftingCluster();
        var controller = host.getCraftingController();
        return !host.isRemoved() && cluster != null && !cluster.isDestroyed()
            && controller != null && !controller.isRemoved() && controller.getCluster() == cluster
            ? controller : null;
    }

    private <T> T registerOffer(T offer, NECraftingCluster cluster, ECOCraftingSystemBlockEntity controller) {
        var network = cluster.getNetworkCluster();
        issuedOffers.put(offer, new OfferScope(cluster, controller, controller.getMainNode().getGrid(), network,
            cluster.getDispatchGeneration(), network == null ? 0 : network.getDispatchGeneration()));
        return offer;
    }

    private boolean validatesOffer(Object offer, ECOCraftingWorkerBlockEntity worker,
                                   ECOVerifiedFastPathRecipe recipe, boolean virtual) {
        var scope = issuedOffers.get(offer);
        var controller = currentController();
        if (scope == null || controller == null || controller != scope.controller()
                || host.getCraftingCluster() != scope.cluster() || controller.getMainNode().getGrid() != scope.grid()) {
            return false;
        }
        var cluster = scope.cluster();
        var network = cluster.getNetworkCluster();
        return cluster.getDispatchGeneration() == scope.clusterGeneration()
            && network == scope.network() && (network == null || network.getDispatchGeneration() == scope.networkGeneration())
            && cluster.isDispatchCandidate(worker)
            && recipe.isIssuedBy(cluster.getFastPathCache())
            && controller.isFullVirtualCraftingMode() == virtual;
    }

    private record OfferScope(NECraftingCluster cluster, ECOCraftingSystemBlockEntity controller, @Nullable IGrid grid,
                              @Nullable NECraftingNetworkCluster network, long clusterGeneration,
                              long networkGeneration) {}

    @Nullable
    ECOCraftingPatternBusBlockEntity.VirtualFastPathOffer findVirtualFastPathOffer(
        ECOExtractedPatternExecution execution
    ) {
        var cluster = host.getCraftingCluster();
        ECOCraftingSystemBlockEntity controller = currentController();
        if (cluster == null || controller == null || !controller.isFullVirtualCraftingMode()) {
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
        var best = cluster.getDispatchCandidates().best();
        return best == null || !cluster.isDispatchCandidate(best.worker())
            ? null
            : registerOffer(new ECOCraftingPatternBusBlockEntity.VirtualFastPathOffer(best.worker(), lookup.recipe()),
                cluster, controller);
    }

    @Nullable
    ECOCraftingPatternBusBlockEntity.BatchFastPathOffer findBatchFastPathOffer(
        ECOExtractedPatternExecution execution,
        int requestedBatchSize
    ) {
        var cluster = host.getCraftingCluster();
        var controller = currentController();
        if (cluster == null || controller == null || requestedBatchSize <= 0 || controller.isFullVirtualCraftingMode()) {
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
        var best = cluster.getDispatchCandidates().best();
        if (best == null || !cluster.isDispatchCandidate(best.worker())) {
            return null;
        }
        // calculateBatchOfferSize is monotone in the worker's free slots, so the highest-ranked candidate also
        // has the largest offer. Taking it keeps a batch concentrated on one worker instead of splitting it.
        int maxBatchSize = ECOCraftingPatternBusBlockEntity.calculateBatchOfferSize(
            requestedBatchSize,
            best.availableSlots(),
            controller.getDispatchThreadCapacity(),
            statefulDispatchLimit(verifiedRecipe, execution)
        );
        if (maxBatchSize <= 0) {
            return null;
        }
        return registerOffer(new ECOCraftingPatternBusBlockEntity.BatchFastPathOffer(best.worker(), verifiedRecipe,
            maxBatchSize), cluster, controller);
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

    boolean recoverJobToNetwork(UUID craftingJobId, appeng.api.storage.MEStorage storage) {
        var cluster = host.getCraftingCluster();
        if (cluster == null) {
            return false;
        }
        boolean recoveredAll = true;
        for (var worker : ECOCraftingJobLifecycle.loadedWorkersForJob(host.getLevel(), craftingJobId)) {
            if (cluster.isDispatchCandidate(worker) && !worker.recoverJobToNetwork(craftingJobId, storage)) {
                recoveredAll = false;
            }
        }
        return recoveredAll;
    }

    boolean isBusy() {
        var cluster = host.getCraftingCluster();
        return cluster == null
            || currentController() == null
            || !cluster.hasAvailableDispatchCandidate();
    }

    int availableThreadSlots() {
        var cluster = host.getCraftingCluster();
        if (cluster == null || currentController() == null) {
            return 0;
        }
        return cluster.getDispatchCandidates().availableSlots();
    }
}
