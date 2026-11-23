package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGrid;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Publishes pattern inventory and topology changes to Machine Interfaces. */
final class ECOCraftingPatternBusPublisher {
    private final ECOCraftingPatternBusBlockEntity host;
    private final Set<ECOMachineInterfaceBlockEntity<?>> subscribers = new LinkedHashSet<>();
    private List<ECOMachineInterfaceBlockEntity<?>> subscriberSnapshot = List.of();

    ECOCraftingPatternBusPublisher(ECOCraftingPatternBusBlockEntity host) {
        this.host = host;
    }

    void subscribe(ECOMachineInterfaceBlockEntity<?> machineInterface, boolean mapped) {
        if (mapped ? subscribers.add(machineInterface) : subscribers.remove(machineInterface)) {
            subscriberSnapshot = List.copyOf(subscribers);
        }
    }

    void notifyPatternInterfaceHosts(int slot) {
        if (host.getLevel() == null || host.getLevel().isClientSide() || host.getMainNode().getGrid() == null) {
            return;
        }
        for (var machineInterface : subscriberSnapshot) {
            machineInterface.onPatternBusInventoryChanged(host, slot);
        }
    }

    void notifyPatternInterfaceHosts(int[] slots) {
        if (host.getLevel() == null || host.getLevel().isClientSide() || host.getMainNode().getGrid() == null) {
            return;
        }
        for (var machineInterface : subscriberSnapshot) {
            machineInterface.onPatternBusInventoryChanged(host, slots);
        }
    }

    void notifyPatternInterfaceTopologyChanged(@Nullable IGrid grid) {
        if (host.getLevel() == null || host.getLevel().isClientSide()) return;
        for (var machineInterface : subscribers) machineInterface.onPatternBusTopologyChanged(host);
        if (grid != null) for (var machineInterface : grid.getMachines(ECOMachineInterfaceBlockEntity.class)) {
            if (!subscribers.contains(machineInterface) && machineInterface.mayMapPatternBus(host)) {
                machineInterface.onPatternBusTopologyChanged(host);
            }
        }
    }
}
