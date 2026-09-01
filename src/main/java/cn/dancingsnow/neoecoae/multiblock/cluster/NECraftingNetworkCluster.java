package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.me.CraftingCapabilitySnapshot;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.util.NEMath;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoublePredicate;
import org.jetbrains.annotations.Nullable;

/**
 * Logical pooling of 2-8 network-switch-equipped crafting hosts sharing a frequency and ME grid.
 * This object owns no AE2 grid-node identity of its own - every member still owns its own real
 * hardware and workers. It only supplies the pooled numbers that make the group read, to players,
 * as a single aggregated host.
 */
public class NECraftingNetworkCluster {
    /**
     * Flat crafting draw of a fully virtualized exchange group, in AE per tick. It replaces every scaling
     * crafting charge - per craft, per batch and per occupied thread slot - so the cost of unlimited crafting
     * is exactly this number for the whole group, no matter how many hosts, workers or crafts are in flight.
     */
    public static final long VIRTUAL_CRAFTING_POWER_PER_TICK = 10_000_000L;

    private final List<NECraftingCluster> members = new ArrayList<>();

    /** Group-wide once-per-tick bookkeeping for {@link #VIRTUAL_CRAFTING_POWER_PER_TICK}. */
    private long virtualPowerTick = Long.MIN_VALUE;
    private boolean virtualPowerPaid = false;

    /**
     * Fast-path knowledge shared by every member of this exchange group: once one worker has really run
     * {@code assemble()} and passed verification, every other worker in the group can execute the same recipe
     * without a cold start. Only recipe-level results live here - worker eligibility and worker capacity are
     * still decided per worker, per dispatch.
     */
    private final ECOCraftingFastPathCache fastPathCache = new ECOCraftingFastPathCache();

    /** Shared immutable capability result; all members observe the same topology and runtime counters. */
    @Nullable
    private CraftingCapabilitySnapshot capabilitySnapshotCache;

    /** Capacity-only snapshot retained across runtime batch/coolant changes; topology and mode changes clear it. */
    @Nullable
    private CraftingCapabilitySnapshot capabilityCapacityCache;

    public void configure(List<NECraftingCluster> newMembers) {
        capabilitySnapshotCache = null;
        capabilityCapacityCache = null;
        boolean membershipChanged = !isSameMembership(newMembers);
        this.members.clear();
        this.members.addAll(newMembers);
        this.members.sort(Comparator.comparing(
            member -> member.getController().getBlockPos(),
            Comparator.<net.minecraft.core.BlockPos>comparingInt(pos -> pos.getX())
                .thenComparingInt(pos -> pos.getY())
                .thenComparingInt(pos -> pos.getZ())
        ));
        if (membershipChanged) {
            // Regrouping may bring in hosts from a different structure generation. Dropping the shared
            // knowledge costs one cold start per recipe and can never execute a stale verification.
            fastPathCache.clear();
        }
        synchronizeUiState();
    }

    private boolean isSameMembership(List<NECraftingCluster> newMembers) {
        if (members.size() != newMembers.size()) {
            return false;
        }
        for (NECraftingCluster candidate : newMembers) {
            boolean found = false;
            for (NECraftingCluster member : members) {
                if (member == candidate) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    public ECOCraftingFastPathCache getFastPathCache() {
        return fastPathCache;
    }

    /** Called when a member's runtime counters or coolant changes without changing advertised batch capacity. */
    public void invalidateCapabilitySnapshot() {
        capabilitySnapshotCache = null;
    }

    /** Called when topology or an overclock mode changes a worker's advertised batch capacity. */
    public void invalidateCapabilityCapacity() {
        capabilitySnapshotCache = null;
        capabilityCapacityCache = null;
    }

    public CraftingCapabilitySnapshot.Capacity getBatchPerFxCapacity() {
        return getCapabilityCapacitySnapshot().batchPerFx();
    }

    public CraftingCapabilitySnapshot.Capacity getTotalBatchCapacity() {
        return getCapabilityCapacitySnapshot().totalBatchCapacity();
    }

    public boolean isVirtualMode() {
        return getCapabilityCapacitySnapshot().virtualMode();
    }

    /**
     * Every worker that is topologically able to execute a pattern for this exchange group right now.
     *
     * <p>The list is rebuilt per dispatch on purpose: it must never retain a reference to a worker whose
     * multiblock was rebuilt or whose block entity was removed, so stale entries are filtered out here rather
     * than cached and revalidated later.
     */
    public List<ECOCraftingWorkerBlockEntity> collectCandidateWorkers() {
        List<ECOCraftingWorkerBlockEntity> candidates = new ArrayList<>();
        for (NECraftingCluster member : members) {
            if (member.isDestroyed() || member.getController() == null) {
                continue;
            }
            for (ECOCraftingWorkerBlockEntity worker : member.getWorkers()) {
                if (worker.isRemoved() || worker.getCluster() != member) {
                    continue;
                }
                candidates.add(worker);
            }
        }
        return candidates;
    }

    /**
     * The first member is the stable authority for persisted UI settings when a logical network is
     * (re)formed. Afterwards every UI mutation is broadcast to all members.
     */
    private void synchronizeUiState() {
        ECOCraftingSystemBlockEntity leader = getLeaderController();
        if (leader == null) {
            return;
        }
        setOverclocked(leader.isLocallyOverclocked());
        setActiveCooling(leader.isLocallyActiveCooling());
    }

    private ECOCraftingSystemBlockEntity getLeaderController() {
        return members.isEmpty() ? null : members.getFirst().getController();
    }

    /** Allocation-free counterpart of {@link #collectCandidateWorkers()} for busy checks. */
    public boolean hasAvailableCandidateWorker() {
        for (NECraftingCluster member : members) {
            if (member.isDestroyed() || member.getController() == null) {
                continue;
            }
            for (ECOCraftingWorkerBlockEntity worker : member.getWorkers()) {
                if (worker.isRemoved() || worker.getCluster() != member) {
                    continue;
                }
                if (worker.getAvailableThreadSlots() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isOverclocked() {
        ECOCraftingSystemBlockEntity leader = getLeaderController();
        return leader != null && leader.isLocallyOverclocked();
    }

    public void setOverclocked(boolean overclocked) {
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller != null) controller.applyNetworkOverclocked(overclocked);
        }
    }

    public boolean isActiveCooling() {
        ECOCraftingSystemBlockEntity leader = getLeaderController();
        return leader != null && leader.isLocallyActiveCooling();
    }

    public void setActiveCooling(boolean activeCooling) {
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller != null) controller.applyNetworkActiveCooling(activeCooling);
        }
    }

    public List<NECraftingCluster> getMembers() {
        return List.copyOf(members);
    }

    public int getNormalSwitchHostCount() {
        // Plain loop: this feeds getCombinedSwitchMultiplier, which is consulted once per worker per dispatch.
        int count = 0;
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller != null && controller.hasNormalNetworkSwitch()) {
                count++;
            }
        }
        return count;
    }

    public int getHighEnergySwitchHostCount() {
        int count = 0;
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller != null && controller.hasHighEnergyNetworkSwitch()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Combined capacity multiplier contributed by every host in this exchange group.
     * A logical network cluster is only created for groups of at least two hosts.
     */
    public int getCombinedSwitchMultiplier() {
        return getCapabilitySnapshot().networkMultiplier();
    }

    /** The sole derived capability state for every host and worker in this logical network. */
    public CraftingCapabilitySnapshot getCapabilitySnapshot() {
        if (capabilitySnapshotCache != null) {
            return capabilitySnapshotCache;
        }
        CraftingCapabilitySnapshot snapshot = buildCapabilitySnapshot();
        capabilitySnapshotCache = snapshot;
        return snapshot;
    }

    /**
     * Computes the full view. Capacity callers use a separate cache so runtime changes do not force a topology
     * rebuild on every ordinary dispatch attempt.
     */
    private CraftingCapabilitySnapshot buildCapabilitySnapshot() {
        int physicalFxCount = 0;
        int activeFxCount = 0;
        int runningBatchCount = 0;
        long ftParallelCapacity = 0L;
        int coolantAmount = 0;
        int coolantCapacity = 0;
        int coolantMaxOverclock = -1;
        ECOCraftingSystemBlockEntity leader = getLeaderController();
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller == null) {
                continue;
            }
            physicalFxCount = saturatingIntAdd(physicalFxCount, member.getWorkers().size());
            ftParallelCapacity = NEMath.saturatingAdd(ftParallelCapacity, controller.getLocalFtParallelCapacity());
            coolantAmount = saturatingIntAdd(coolantAmount, controller.getCoolant());
            coolantCapacity = saturatingIntAdd(coolantCapacity, ECOCraftingSystemBlockEntity.MAX_COOLANT);
            coolantMaxOverclock = Math.max(coolantMaxOverclock, controller.getLocalCoolingMaxOverclock());
            for (ECOCraftingWorkerBlockEntity worker : member.getWorkers()) {
                if (worker.isWorking()) {
                    activeFxCount = saturatingIntAdd(activeFxCount, 1);
                }
                runningBatchCount = saturatingIntAdd(runningBatchCount, worker.getRunningBatchCount());
            }
        }
        boolean overclocked = leader != null && leader.isLocallyOverclocked();
        boolean activeCooling = leader != null && leader.isLocallyActiveCooling();
        int powerMultiplier = leader == null ? 1 : leader.getTier().getOverclockedCrafterPowerMultiply();
        return CraftingCapabilitySnapshot.calculate(new CraftingCapabilitySnapshot.Input(
            physicalFxCount,
            activeFxCount,
            getNormalSwitchHostCount(),
            getHighEnergySwitchHostCount(),
            CraftingCapabilitySnapshot.F9_OVERCLOCKED_BATCH_PER_FX,
            ftParallelCapacity,
            runningBatchCount,
            overclocked,
            activeCooling,
            powerMultiplier,
            isEndgameEligible(),
            new CraftingCapabilitySnapshot.CoolantState(
                activeCooling, coolantAmount, coolantCapacity, coolantMaxOverclock)
        ));
    }

    private CraftingCapabilitySnapshot getCapabilityCapacitySnapshot() {
        if (capabilityCapacityCache == null) {
            capabilityCapacityCache = buildCapabilitySnapshot();
        }
        return capabilityCapacityCache;
    }

    private static int saturatingIntAdd(int left, int right) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, left) + Math.max(0, right));
    }

    public int getThreadCount() {
        return (int) Math.min(Integer.MAX_VALUE, getCapabilitySnapshot().ftParallelCapacity());
    }

    public int getRunningThreadCount() {
        return getCapabilitySnapshot().runningBatchCount();
    }

    public int getCoolantAmount() {
        long total = 0;
        for (NECraftingCluster member : members) {
            var controller = member.getController();
            if (controller != null) total = NEMath.saturatingAdd(total, controller.getCoolant());
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int getCoolantCapacity() {
        long total = (long) ECOCraftingSystemBlockEntity.MAX_COOLANT * members.size();
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int getCoolantMaxOverclock() {
        int maximum = -1;
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller != null) maximum = Math.max(maximum, controller.getLocalCoolingMaxOverclock());
        }
        return maximum;
    }

    public FluidStack getCoolantFluid() {
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller != null && controller.getCoolant() > 0 && !controller.getCurrentCoolantFluid().isEmpty()) {
                return controller.getCurrentCoolantFluid();
            }
        }
        return FluidStack.EMPTY;
    }

    public int getCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (requestedCrafts <= 0 || coolantPerCraft <= 0) return Math.max(0, requestedCrafts);
        int total = 0;
        for (NECraftingCluster member : members) {
            var controller = member.getController();
            if (controller != null) total = Math.min(requestedCrafts,
                total + controller.getLocalCraftingCoolantCraftLimit(coolantPerCraft, requiredOverclock, requestedCrafts - total));
        }
        return total;
    }

    public boolean tryConsumeCoolant(int amount, int requiredOverclock) {
        int remaining = amount;
        for (NECraftingCluster member : members) {
            var controller = member.getController();
            if (controller == null) continue;
            int available = controller.getLocalCraftingCoolantCraftLimit(1, requiredOverclock, remaining);
            if (available > 0 && controller.tryConsumeLocalCoolant(available, requiredOverclock)) remaining -= available;
            if (remaining <= 0) return true;
        }
        return remaining <= 0;
    }

    public boolean isLeader(NECraftingCluster member) {
        return !members.isEmpty() && members.getFirst() == member;
    }

    /**
     * Exactly 8 members, all at max tier, max structural build length, and high-energy switches -
     * the unconditional endgame override. No cooling-controller precondition.
     */
    public boolean isEndgameEligible() {
        List<CraftingCapabilitySnapshot.VirtualHost> topology = new ArrayList<>(members.size());
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller == null) {
                return false;
            }
            topology.add(new CraftingCapabilitySnapshot.VirtualHost(
                controller.getTier().getTier() == ECOTier.L9.getTier(),
                controller.hasHighEnergyNetworkSwitch(),
                member.getWorkers().size(),
                controller.getMaxBuildLength()
            ));
        }
        return CraftingCapabilitySnapshot.isVirtualTopologyEligible(topology);
    }

    public int getTotalParallelism() {
        return (int) Math.min(Integer.MAX_VALUE, getCapabilitySnapshot().ftParallelCapacity());
    }

    public boolean hasCoolant(int amount, int requiredOverclock) {
        int available = 0;
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller == null) continue;
            available = saturatingIntAdd(available,
                controller.getLocalAvailableCoolant(amount - available, requiredOverclock));
            if (available >= amount) return true;
        }
        return available >= amount;
    }

    public int getTotalCraftingCapability() {
        CraftingCapabilitySnapshot.Capacity capacity = getCapabilitySnapshot().totalBatchCapacity();
        return capacity.unlimited() ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, capacity.finiteValue());
    }

    /**
     * Charges {@link #VIRTUAL_CRAFTING_POWER_PER_TICK} at most once per game tick for the whole group, and
     * reports whether this tick is paid for. Every host and every thread of the group asks the same object, so
     * the group is billed once no matter how many of them are working; the answer is memoized per tick so a
     * second caller neither pays again nor gets a different answer.
     *
     * @param extractor pays the given amount from the shared ME grid, returning whether it was paid in full
     */
    public boolean tryConsumeVirtualCraftingPower(long currentTick, DoublePredicate extractor) {
        if (virtualPowerTick == currentTick) {
            return virtualPowerPaid;
        }
        virtualPowerTick = currentTick;
        virtualPowerPaid = extractor.test(VIRTUAL_CRAFTING_POWER_PER_TICK);
        return virtualPowerPaid;
    }
}
