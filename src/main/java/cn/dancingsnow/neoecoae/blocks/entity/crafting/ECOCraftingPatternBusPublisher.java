package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.IGridNodeListener;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Owns AE2 provider refreshes and Machine Interface notifications for one pattern bus. */
final class ECOCraftingPatternBusPublisher {
    private final ECOCraftingPatternBusBlockEntity host;
    private boolean providerRefreshQueued;

    ECOCraftingPatternBusPublisher(ECOCraftingPatternBusBlockEntity host) {
        this.host = host;
    }

    void notifyPatternInterfaceHosts(int slot) {
        if (host.getLevel() == null || host.getLevel().isClientSide || host.getMainNode().getGrid() == null) {
            return;
        }
        for (var machineInterface : host.getMainNode().getGrid()
                .getActiveMachines(cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity.class)) {
            machineInterface.onPatternBusInventoryChanged(host, slot);
        }
    }

    void notifyPatternInterfaceHosts(int[] slots) {
        if (host.getLevel() == null || host.getLevel().isClientSide || host.getMainNode().getGrid() == null) {
            return;
        }
        for (var machineInterface : host.getMainNode().getGrid()
                .getActiveMachines(cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity.class)) {
            machineInterface.onPatternBusInventoryChanged(host, slots);
        }
    }

    void notifyPatternInterfaceTopologyChanged() {
        notifyPatternInterfaceTopologyChanged(host.getMainNode().getGrid());
    }

    void notifyPatternInterfaceTopologyChanged(@Nullable IGrid grid) {
        if (host.getLevel() == null || host.getLevel().isClientSide || grid == null) {
            return;
        }
        for (var machineInterface : grid
                .getActiveMachines(cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity.class)) {
            machineInterface.onPatternBusTopologyChanged(host);
        }
    }

    /** Re-mount the provider after AE2 has completed a power or pathing transition. */
    void queueCraftingProviderRefresh() {
        if (!(host.getLevel() instanceof ServerLevel serverLevel)
            || providerRefreshQueued
            || host.isServerStoppingForPublisher()) {
            return;
        }

        providerRefreshQueued = true;
        var server = serverLevel.getServer();
        int targetTick = server.getTickCount() + 1;

        server.tell(new TickTask(targetTick, () -> {
            providerRefreshQueued = false;

            if (!host.isServerStoppingForPublisher()
                && !host.isRemoved()
                && host.getLevel() == serverLevel
                && host.getMainNode().isOnline()) {
                ICraftingProvider.requestUpdate(host.getMainNode());
            }
        }));
    }
}
