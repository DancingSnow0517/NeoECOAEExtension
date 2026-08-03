package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Schedules a group of physical crafting hosts that share one AE2 grid. */
public final class NECraftingNetworkCluster {
    private static final Comparator<NECraftingCluster> HOST_ORDER =
            Comparator.comparingLong(cluster -> cluster.getController() == null
                    ? Long.MAX_VALUE
                    : cluster.getController().getBlockPos().asLong());

    private List<NECraftingCluster> members = List.of();
    private List<ECOCraftingSystemBlockEntity> controllers = List.of();
    private List<ECOCraftingWorkerBlockEntity> workers = List.of();
    private List<ECOCraftingPatternBusBlockEntity> patternBuses = List.of();
    private int nextMemberIndex;
    private int nextCoolantControllerIndex;
    private boolean overclocked;
    private boolean activeCooling;

    public void configure(Collection<NECraftingCluster> source) {
        members = source.stream()
                .filter(cluster -> cluster != null && !cluster.isDestroyed() && cluster.getController() != null)
                .sorted(HOST_ORDER)
                .toList();
        List<ECOCraftingWorkerBlockEntity> nextWorkers = new ArrayList<>();
        List<ECOCraftingPatternBusBlockEntity> nextPatternBuses = new ArrayList<>();
        List<ECOCraftingSystemBlockEntity> nextControllers = new ArrayList<>();
        for (NECraftingCluster member : members) {
            nextControllers.add(member.getController());
            nextWorkers.addAll(member.getWorkers());
            nextPatternBuses.addAll(member.getPatternBuses());
            member.getController().markStructureStatsDirty();
        }
        nextWorkers.sort(Comparator.comparingLong(worker -> worker.getBlockPos().asLong()));
        nextPatternBuses.sort(Comparator.comparingLong(bus -> bus.getBlockPos().asLong()));
        workers = List.copyOf(nextWorkers);
        patternBuses = List.copyOf(nextPatternBuses);
        controllers = List.copyOf(nextControllers);
        if (controllers.isEmpty()) {
            overclocked = false;
            activeCooling = false;
        } else {
            overclocked = controllers.get(0).isOverclocked();
            activeCooling = controllers.get(0).isActiveCooling();
            for (ECOCraftingSystemBlockEntity controller : controllers) {
                controller.setNetworkOverclocked(overclocked);
                controller.setNetworkActiveCooling(activeCooling);
            }
        }
        nextMemberIndex = Math.floorMod(nextMemberIndex, Math.max(1, members.size()));
    }

    public void clear() {
        for (NECraftingCluster member : members) {
            member.getController().markStructureStatsDirty();
        }
        members = List.of();
        controllers = List.of();
        workers = List.of();
        patternBuses = List.of();
        nextMemberIndex = 0;
        nextCoolantControllerIndex = 0;
        overclocked = false;
        activeCooling = false;
    }

    public int getMemberCount() {
        return members.size();
    }

    public void onCoolingAvailabilityChanged() {
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            controller.refreshExchangeThreadCount();
        }
    }

    public List<ECOCraftingWorkerBlockEntity> getWorkers() {
        return workers;
    }

    public int getCoolantAmount() {
        long total = 0L;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            total = Math.min(Integer.MAX_VALUE, total + controller.getCoolant());
        }
        return (int) total;
    }

    public int getCoolantCapacity() {
        return (int) Math.min(Integer.MAX_VALUE, (long) ECOCraftingSystemBlockEntity.MAX_COOLANT * controllers.size());
    }

    public int getCoolingMaxOverclock() {
        if (!activeCooling) {
            return -1;
        }
        int maximum = -1;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            maximum = Math.max(maximum, controller.getLocalCoolingMaxOverclock());
        }
        return maximum;
    }

    /**
     * Network exchange is enabled only while at least one host can sustain the
     * requested cooling tier. The configured switch remains visible, but the
     * runtime multiplier falls back to ordinary crafting until cooling is usable.
     */
    public boolean hasCoolingForNetworkMultiplier(int multiplier) {
        if (!activeCooling || multiplier <= 1) {
            return multiplier <= 1;
        }
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            if (controller.hasLocalCoolingForNetworkMultiplier(multiplier)) {
                return true;
            }
        }
        return false;
    }

    @Nullable public String getDisplayedCoolantFluidId() {
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            var fluidId = controller.getLocalCoolantFluidId();
            if (fluidId != null) {
                return fluidId.toString();
            }
        }
        return null;
    }

    public int getCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (!activeCooling || requestedCrafts <= 0 || coolantPerCraft <= 0) {
            return Math.max(0, requestedCrafts);
        }
        long available = 0L;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            available = Math.min(
                    Integer.MAX_VALUE,
                    available
                            + controller.getLocalCraftingCoolantCraftLimit(
                                    coolantPerCraft, requiredOverclock, requestedCrafts));
            if (available >= requestedCrafts) {
                return requestedCrafts;
            }
        }
        return (int) available;
    }

    public boolean tryConsumeCoolant(int amount, int requiredOverclock) {
        if (!activeCooling || amount <= 0) {
            return true;
        }
        if (getCraftingCoolantCraftLimit(1, requiredOverclock, amount) < amount || controllers.isEmpty()) {
            return false;
        }

        int remaining = amount;
        int start = Math.floorMod(nextCoolantControllerIndex, controllers.size());
        for (int offset = 0; offset < controllers.size() && remaining > 0; offset++) {
            ECOCraftingSystemBlockEntity controller = controllers.get((start + offset) % controllers.size());
            int participantsLeft = controllers.size() - offset;
            int fairShare = (remaining + participantsLeft - 1) / participantsLeft;
            int available = controller.getLocalCraftingCoolantCraftLimit(1, requiredOverclock, fairShare);
            int consumed = Math.min(fairShare, available);
            if (consumed > 0 && !controller.tryConsumeLocalCoolant(consumed, requiredOverclock)) {
                return false;
            }
            remaining -= consumed;
        }
        nextCoolantControllerIndex = (start + 1) % controllers.size();
        return remaining == 0;
    }

    public void clearCoolant() {
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            controller.clearLocalCoolant();
        }
        onCoolingAvailabilityChanged();
    }

    public boolean isOverclocked() {
        return overclocked;
    }

    public boolean isActiveCooling() {
        return activeCooling;
    }

    public void setOverclocked(boolean value) {
        overclocked = value;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            controller.setNetworkOverclocked(value);
        }
    }

    public void setActiveCooling(boolean value) {
        activeCooling = value;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            controller.setNetworkActiveCooling(value);
        }
        onCoolingAvailabilityChanged();
    }

    public List<IPatternDetails> getMergedPatterns() {
        Map<PatternSignature, IPatternDetails> patterns = new LinkedHashMap<>();
        for (ECOCraftingPatternBusBlockEntity bus : patternBuses) {
            for (IPatternDetails pattern : bus.getLocalAvailablePatterns()) {
                patterns.putIfAbsent(PatternSignature.of(pattern), pattern);
            }
        }
        return List.copyOf(patterns.values());
    }

    public int getThreadCount() {
        int total = 0;
        for (NECraftingCluster member : members) {
            total = saturatingAdd(total, member.getController().getLocalThreadCount());
        }
        return total;
    }

    public int getRunningThreadCount() {
        int total = 0;
        for (NECraftingCluster member : members) {
            total = saturatingAdd(total, member.getController().getLocalRunningThreadCount());
        }
        return total;
    }

    public int getAvailableThreads() {
        return Math.max(0, getThreadCount() - getRunningThreadCount());
    }

    /** Largest batch that can run in a single thread on the requested grid. */
    public int getMaxBatchPerThread(@Nullable IGrid grid) {
        int maxBatch = 0;
        for (NECraftingCluster member : members) {
            if (grid != null
                    && member.getWorkers().stream()
                            .noneMatch(worker -> worker.getMainNode().getGrid() == grid)) {
                continue;
            }
            ECOCraftingSystemBlockEntity controller = member.getController();
            maxBatch = Math.max(maxBatch, controller.getLocalMaxBatchPerThread());
        }
        return maxBatch;
    }

    /** Runtime per-host batch data for the crafting host statistics tooltip. */
    public List<HostBatchInfo> getHostBatchInfos() {
        List<HostBatchInfo> result = new ArrayList<>(members.size());
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            result.add(new HostBatchInfo(
                    member.isHighEnergyNetworkMode(),
                    controller.getLocalThreadCount(),
                    controller.isVirtualCraftingMode() ? Long.MAX_VALUE : controller.getLocalMaxBatchPerThread()));
        }
        return List.copyOf(result);
    }

    /**
     * Calculates the per-recipe time ratio for the shared exchange.
     * Batch capacity and host count are reported separately; neither changes the duration of a
     * single recipe and must not be folded into a percentage that looks like machine speed.
     */
    public double getTimeMultiplier() {
        if (controllers.isEmpty()) {
            return 1.0D;
        }
        int theoreticalTicks = 0;
        for (ECOCraftingSystemBlockEntity controller : controllers) {
            theoreticalTicks = Math.max(theoreticalTicks, controller.getTheoreticalCraftTicks());
        }
        return ECOCraftingSystemBlockEntity.calculateTimeMultiplier(theoreticalTicks);
    }

    /** Per-host runtime values shown in the host UI tooltip. */
    public record HostBatchInfo(boolean highEnergy, int threadCount, long maxBatchPerThread) {}

    /** Batch capacity includes the x2/x8 switch multiplier; task lanes do not. */
    private int getAvailableBatchSlots(@Nullable IGrid grid) {
        int total = 0;
        for (NECraftingCluster member : members) {
            if (grid != null
                    && member.getWorkers().stream()
                            .noneMatch(worker -> worker.getMainNode().getGrid() == grid)) {
                continue;
            }
            total = saturatingAdd(total, member.getController().getCurrentBatchSlots());
        }
        return total;
    }

    public boolean tryPushPattern(
            @Nullable IGrid grid, ECOExtractedPatternExecution execution, @Nullable UUID craftingJobId) {
        if (execution.molecularPattern() == null || getAvailableThreads() <= 0 || members.isEmpty()) {
            return false;
        }
        int start = Math.floorMod(nextMemberIndex, members.size());
        for (int memberOffset = 0; memberOffset < members.size(); memberOffset++) {
            int memberIndex = (start + memberOffset) % members.size();
            NECraftingCluster member = members.get(memberIndex);
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller.getLocalAvailableThreads() <= 0) {
                continue;
            }
            for (ECOCraftingWorkerBlockEntity worker : member.getWorkers()) {
                if (grid != null && worker.getMainNode().getGrid() != grid) {
                    continue;
                }
                if (worker.pushPattern(execution, craftingJobId)) {
                    nextMemberIndex = (memberIndex + 1) % members.size();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean tryPushBatch(@Nullable IGrid grid, ECOBatchCraftingRequest request) {
        ECOCraftingPatternBusBlockEntity.BatchFastPathOffer offer =
                findBatchFastPathOffer(grid, request.key(), null, request, request.batchSize());
        return tryPushBatch(grid, request, offer);
    }

    public boolean tryPushBatch(
            @Nullable IGrid grid,
            ECOBatchCraftingRequest request,
            @Nullable ECOCraftingPatternBusBlockEntity.BatchFastPathOffer offer) {
        if (members.isEmpty() || offer == null) {
            return false;
        }
        if (getAvailableThreads() <= 0) {
            return false;
        }
        ECOCraftingWorkerBlockEntity worker = offer.worker();
        if (!workers.contains(worker) || (grid != null && worker.getMainNode().getGrid() != grid)) {
            return false;
        }
        NECraftingCluster member = worker.getCluster();
        ECOCraftingSystemBlockEntity controller = member == null ? null : member.getController();
        if (controller != null
                && offer.maxBatchSize() >= request.batchSize()
                && controller.getCurrentBatchSlots() > 0
                && (controller.isVirtualCraftingMode()
                        || controller.getLargestAvailableCraftingBatchSize() >= request.batchSize())
                && worker.getAvailableThreadSlots() > 0
                && worker.pushBatch(request, offer.result())) {
            int memberIndex = members.indexOf(member);
            if (memberIndex >= 0) {
                nextMemberIndex = (memberIndex + 1) % members.size();
            }
            return true;
        }
        return false;
    }

    @Nullable public ECOCraftingPatternBusBlockEntity.BatchFastPathOffer findBatchFastPathOffer(
            @Nullable IGrid grid,
            ECOFastPathKey key,
            @Nullable ECOExtractedPatternExecution execution,
            @Nullable ECOBatchCraftingRequest request,
            long requestedBatchSize) {
        if (requestedBatchSize <= 0 || members.isEmpty() || getAvailableThreads() <= 0) {
            return null;
        }

        int start = Math.floorMod(nextMemberIndex, members.size());
        ECOCraftingPatternBusBlockEntity.BatchFastPathOffer bestOffer = null;
        for (int memberOffset = 0; memberOffset < members.size(); memberOffset++) {
            int memberIndex = (start + memberOffset) % members.size();
            NECraftingCluster member = members.get(memberIndex);
            if (grid != null
                    && member.getWorkers().stream()
                            .noneMatch(worker -> worker.getMainNode().getGrid() == grid)) {
                continue;
            }
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller.getCurrentBatchSlots() <= 0) {
                continue;
            }
            long availableBatchSize = controller.isVirtualCraftingMode()
                    ? Long.MAX_VALUE
                    : controller.getLargestAvailableCraftingBatchSize();
            if (availableBatchSize <= 0) {
                continue;
            }

            List<ECOCraftingWorkerBlockEntity> localWorkers = member.getWorkers();
            for (ECOCraftingWorkerBlockEntity worker : localWorkers) {
                if (grid != null && worker.getMainNode().getGrid() != grid) {
                    continue;
                }
                if (worker.getAvailableThreadSlots() <= 0) {
                    continue;
                }
                ECOFastPathResult result = execution == null
                        ? worker.getFastPathCache().peek(key)
                        : worker.getVerifiedFastPathResult(execution);
                if (result == null || result.isNegative()) {
                    continue;
                }
                if (request != null && !result.matchesBatchRequest(request)) {
                    worker.getFastPathCache().recordExpectedMismatch();
                    continue;
                }
                long maxBatchSize = Math.min(requestedBatchSize, availableBatchSize);
                if (maxBatchSize > 0 && (bestOffer == null || maxBatchSize > bestOffer.maxBatchSize())) {
                    bestOffer = new ECOCraftingPatternBusBlockEntity.BatchFastPathOffer(worker, result, maxBatchSize);
                    if (maxBatchSize >= requestedBatchSize) {
                        return bestOffer;
                    }
                    break;
                }
            }
        }
        return bestOffer;
    }

    public boolean isBusy(@Nullable IGrid grid) {
        if (getAvailableThreads() <= 0) {
            return true;
        }
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            if ((grid == null || worker.getMainNode().getGrid() == grid) && !worker.isBusy()) {
                return false;
            }
        }
        return true;
    }

    private static int saturatingAdd(int left, int right) {
        return right > Integer.MAX_VALUE - left ? Integer.MAX_VALUE : left + Math.max(0, right);
    }

    /**
     * AE2 may decode identical encoded patterns into separate object instances
     * on each host. Merge by their immutable recipe shape so a shared network
     * advertises one pattern instead of one copy per pattern bus.
     */
    private record PatternSignature(
            @Nullable AEItemKey definition,
            Class<?> patternType,
            List<InputSignature> inputs,
            List<GenericStack> outputs,
            boolean supportsExternalPush,
            @Nullable IPatternDetails fallbackIdentity) {
        private static PatternSignature of(IPatternDetails pattern) {
            try {
                List<InputSignature> inputs = Arrays.stream(pattern.getInputs())
                        .map(input -> new InputSignature(
                                List.copyOf(Arrays.asList(input.getPossibleInputs())), input.getMultiplier()))
                        .toList();
                return new PatternSignature(
                        pattern.getDefinition(),
                        pattern.getClass(),
                        inputs,
                        List.copyOf(Arrays.asList(pattern.getOutputs())),
                        pattern.supportsPushInputsToExternalInventory(),
                        null);
            } catch (RuntimeException ignored) {
                // Third-party dynamic patterns may not expose a stable shape.
                // Keep those instances distinct instead of hiding a recipe.
                return new PatternSignature(null, pattern.getClass(), List.of(), List.of(), false, pattern);
            }
        }
    }

    private record InputSignature(List<GenericStack> possibleInputs, long multiplier) {}
}
