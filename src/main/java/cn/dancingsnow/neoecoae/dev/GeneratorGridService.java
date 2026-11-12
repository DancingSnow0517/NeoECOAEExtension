package cn.dancingsnow.neoecoae.dev;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class GeneratorGridService implements IGridService, IGridServiceProvider {
    private final Map<IGridNode, Object> hosts = new IdentityHashMap<>();

    @Override public void addNode(IGridNode node, CompoundTag data) {
        Object host = node.getOwner();
        if (host instanceof IChestOrDrive || host instanceof DevResourceGenerator.GeneratorBlockEntity) hosts.put(node, host);
    }

    @Override public void removeNode(IGridNode node) { hosts.remove(node); }

    @Override public void onServerEndTick() {
        if (!cn.dancingsnow.neoecoae.config.DevToolsConfig.enabled()) return;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : hosts.entrySet()) {
            IGridNode node = entry.getKey();
            Object host = entry.getValue();
            if (!node.isActive() || !seen.add(host)) continue;
            if (host instanceof DevResourceGenerator.GeneratorBlockEntity generator) {
                generator.state.tick(node.getGrid(), IActionSource.ofMachine(generator), generator::setChanged);
            } else if (host instanceof IChestOrDrive drive && host instanceof AEBaseBlockEntity be && drive.isPowered()) {
                for (int slot = 0; slot < drive.getCellCount(); slot++) {
                    if (drive.getOriginalCellInventory(slot) instanceof GeneratorCell cell) {
                        if (cell.state == null) cell.state = GeneratorState.read(DevResourceGenerator.readItem(cell.stack), be.getLevel().registryAccess());
                        cell.state.tick(node.getGrid(), IActionSource.ofMachine(drive), () -> {
                            DevResourceGenerator.writeItem(cell.stack, cell.state.write(be.getLevel().registryAccess()));
                            be.setChanged();
                        });
                    }
                }
            }
        }
    }

    static final class GeneratorCell implements StorageCell {
        final ItemStack stack;
        GeneratorState state;
        GeneratorCell(ItemStack stack) { this.stack = stack; }
        @Override public CellState getStatus() { return CellState.EMPTY; }
        @Override public double getIdleDrain() { return 0; }
        @Override public boolean canFitInsideCell() { return false; }
        @Override public void persist() {}
        @Override public Component getDescription() { return Component.literal("Dev Random Resource Generator"); }
    }
}
