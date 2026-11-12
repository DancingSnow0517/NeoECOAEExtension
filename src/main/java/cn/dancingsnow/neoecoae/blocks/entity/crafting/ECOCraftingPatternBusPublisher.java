package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGrid;
import org.jetbrains.annotations.Nullable;

/** Publishes pattern inventory and topology changes to Machine Interfaces. */
final class ECOCraftingPatternBusPublisher {
    private final ECOCraftingPatternBusBlockEntity host;

    ECOCraftingPatternBusPublisher(ECOCraftingPatternBusBlockEntity host) {
        this.host = host;
    }

    void notifyPatternInterfaceHosts(int slot) {
        if (host.getLevel() == null || host.getLevel().isClientSide() || host.getMainNode().getGrid() == null) {
            return;
        }
        for (var machineInterface : host.getMainNode().getGrid()
                .getActiveMachines(cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity.class)) {
            machineInterface.onPatternBusInventoryChanged(host, slot);
        }
    }

    void notifyPatternInterfaceHosts(int[] slots) {
        if (host.getLevel() == null || host.getLevel().isClientSide() || host.getMainNode().getGrid() == null) {
            return;
        }
        for (var machineInterface : host.getMainNode().getGrid()
                .getActiveMachines(cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity.class)) {
            machineInterface.onPatternBusInventoryChanged(host, slots);
        }
    }

    void notifyPatternInterfaceTopologyChanged(@Nullable IGrid grid) {
        if (host.getLevel() == null || host.getLevel().isClientSide() || grid == null) {
            return;
        }
        for (var machineInterface : grid
                .getActiveMachines(cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity.class)) {
            machineInterface.onPatternBusTopologyChanged(host);
        }
    }
}
