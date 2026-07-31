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
        if (getAvailableBatchSlots(grid) <= 0 || members.isEmpty()) {
            return false;
        }
        int start = Math.floorMod(nextMemberIndex, members.size());
        for (int memberOffset = 0; memberOffset < members.size(); memberOffset++) {
            int memberIndex = (start + memberOffset) % members.size();
            NECraftingCluster member = members.get(memberIndex);
            if (member.getController().getCurrentBatchSlots() <= 0) {
                continue;
            }
            for (ECOCraftingWorkerBlockEntity worker : member.getWorkers()) {
                if (grid != null && worker.getMainNode().getGrid() != grid) {
                    continue;
                }
                if (worker.pushBatch(request)) {
                    nextMemberIndex = (memberIndex + 1) % members.size();
                    return true;
                }
            }
        }
        return false;
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
