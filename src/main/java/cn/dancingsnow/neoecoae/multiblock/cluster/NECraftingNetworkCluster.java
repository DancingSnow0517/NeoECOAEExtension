package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.util.NEMath;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoublePredicate;
import java.util.function.ToLongFunction;

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

    public void configure(List<NECraftingCluster> newMembers) {
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
        long normalCapacity = NEMath.saturatingMultiply(getNormalSwitchHostCount(), 2L);
        long highEnergyCapacity = NEMath.saturatingMultiply(getHighEnergySwitchHostCount(), 8L);
        return (int) Math.min(Integer.MAX_VALUE, NEMath.saturatingAdd(normalCapacity, highEnergyCapacity));
    }

    public int getThreadCount() {
        // FT parallel cores use the same per-host switch multiplier formula as FX workers.
        long total = sumMultiplied(member -> {
            var controller = member.getController();
            return controller == null ? 0 : controller.getLocalThreadCount();
        });
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int getRunningThreadCount() {
        long total = 0;
        for (NECraftingCluster member : members) {
            var controller = member.getController();
            if (controller != null) total = NEMath.saturatingAdd(total, controller.getLocalRunningThreadCount());
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
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

    private long sumMultiplied(ToLongFunction<NECraftingCluster> getter) {
        long total = 0;
        for (NECraftingCluster member : members) {
            long multiplier = members.size() > 1 ? member.getNetworkMultiplier() : 1;
            total = NEMath.saturatingAdd(total, NEMath.saturatingMultiply(getter.applyAsLong(member), multiplier));
        }
        return total;
    }

    /**
     * Exactly 8 members, all at max tier, max structural build length, and high-energy switches -
     * the unconditional endgame override. No cooling-controller precondition.
     */
    public boolean isEndgameEligible() {
        if (members.size() != 8) {
            return false;
        }
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller == null) {
                return false;
            }
            if (controller.getTier().getTier() != ECOTier.L9.getTier()) {
                return false;
            }
            if (controller.getSelectedBuildLength() != controller.getMaxBuildLength()) {
                return false;
            }
            if (!controller.hasHighEnergyNetworkSwitch()) {
                return false;
            }
        }
        return true;
    }

    public int getTotalParallelism() {
        if (isEndgameEligible()) {
            return Integer.MAX_VALUE;
        }
        long total = sumMultiplied(member -> {
            ECOCraftingSystemBlockEntity controller = member.getController();
            return controller == null ? 0 : controller.getLocalThreadCount();
        });
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int getTotalCraftingCapability() {
        if (isEndgameEligible()) {
            return Integer.MAX_VALUE;
        }
        long localTotal = 0L;
        for (NECraftingCluster member : members) {
            ECOCraftingSystemBlockEntity controller = member.getController();
            if (controller != null) {
                localTotal = NEMath.saturatingAdd(localTotal, controller.getLocalAvailableThreads());
            }
        }
        long total = NEMath.saturatingMultiply(localTotal, Math.max(1L, getCombinedSwitchMultiplier()));
        return (int) Math.min(Integer.MAX_VALUE, total);
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
