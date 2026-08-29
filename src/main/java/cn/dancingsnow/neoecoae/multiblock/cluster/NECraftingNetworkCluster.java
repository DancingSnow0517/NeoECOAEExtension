package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.util.NEMath;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Logical pooling of 2-8 network-switch-equipped crafting hosts sharing a frequency and ME grid.
 * This object owns no AE2 grid-node identity of its own - every member still owns its own real
 * hardware and workers. It only supplies the pooled numbers that make the group read, to players,
 * as a single aggregated host.
 */
public class NECraftingNetworkCluster {
    private final List<NECraftingCluster> members = new ArrayList<>();

    public void configure(List<NECraftingCluster> newMembers) {
        this.members.clear();
        this.members.addAll(newMembers);
        this.members.sort(Comparator.comparing(
            member -> member.getController().getBlockPos(),
            Comparator.<net.minecraft.core.BlockPos>comparingInt(pos -> pos.getX())
                .thenComparingInt(pos -> pos.getY())
                .thenComparingInt(pos -> pos.getZ())
        ));
        synchronizeUiState();
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
        return (int) members.stream()
            .map(NECraftingCluster::getController)
            .filter(java.util.Objects::nonNull)
            .filter(ECOCraftingSystemBlockEntity::hasNormalNetworkSwitch)
            .count();
    }

    public int getHighEnergySwitchHostCount() {
        return (int) members.stream()
            .map(NECraftingCluster::getController)
            .filter(java.util.Objects::nonNull)
            .filter(ECOCraftingSystemBlockEntity::hasHighEnergyNetworkSwitch)
            .count();
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
}
