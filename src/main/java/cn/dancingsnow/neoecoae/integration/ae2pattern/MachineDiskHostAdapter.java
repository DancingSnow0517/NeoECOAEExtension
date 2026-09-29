package cn.dancingsnow.neoecoae.integration.ae2pattern;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.inventories.InternalInventory;
import appeng.api.util.IConfigManager;

import io.github.lounode.ae2pattern.api.IPatternDiskHost;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Supplier;

/**
 * Presents one NEO ECO machine to AE2 Pattern Disk's terminals as a disk host.
 *
 * <p>An adapter rather than the machines implementing {@link IPatternDiskHost} directly: AE2 Pattern Disk is an
 * optional dependency, so a block entity cannot name a type from it without breaking on a machine that does
 * not have the mod.</p>
 *
 * <p>Instances are cached by the caller. Both terminals de-duplicate hosts by object identity, so a fresh
 * adapter per query would make one machine's disks show up once per call.</p>
 *
 * <p>The slots are asked of the machine on every call rather than captured: a host is the thing that knows
 * which inventory holds its disks, and reading it there keeps this adapter from holding a second reference that
 * could drift from the machine's own.</p>
 */
final class MachineDiskHostAdapter implements IPatternDiskHost {

    private final BlockEntity machine;
    private final Supplier<InternalInventory> diskSlots;
    /** The config manager that owns the visibility switch, or {@code null} when the machine has none. */
    private final Supplier<IConfigManager> configManager;

    MachineDiskHostAdapter(BlockEntity machine, Supplier<InternalInventory> diskSlots,
            Supplier<IConfigManager> configManager) {
        this.machine = machine;
        this.diskSlots = diskSlots;
        this.configManager = configManager;
    }

    @Override
    public boolean isVisibleInPatternAccessTerminal() {
        // AE2's own switch, so a machine hidden in AE2's pattern access terminal is hidden here too. Only a
        // machine whose menu is AE2's pattern provider menu has that config manager; one without it stays
        // listed, which is the default this interface documents.
        IConfigManager manager = configManager.get();
        return manager == null || manager.getSetting(Settings.PATTERN_ACCESS_TERMINAL) == YesNo.YES;
    }

    /**
     * @return whether this adapter still speaks for {@code candidate}: the same block entity, still in the
     *         world. A block entity that was removed - unloaded, broken, or replaced at the same position -
     *         must not be handed out again, or the terminal would read and write an inventory that is no
     *         longer part of the level
     */
    boolean speaksFor(BlockEntity candidate) {
        return machine == candidate && !machine.isRemoved();
    }

    @Override
    public InternalInventory getDiskInventory() {
        return diskSlots.get();
    }

    @Override
    public BlockPos getBlockPos() {
        return machine.getBlockPos();
    }
}
