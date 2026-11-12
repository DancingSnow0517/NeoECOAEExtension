package cn.dancingsnow.neoecoae.api.storage;

import cn.dancingsnow.neoecoae.gui.storage.StoragePriority;
import cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Shared cell-priority target validation and per-player selection for built-in and addon hosts.
 * Keep one instance per host. All server calls must run on its owning server thread.
 */
public final class ECOStorageCellPriorities {
    public record Target(BlockPos position, UUID installation) {
        public Target {
            if (position != null) position = position.immutable();
        }
    }

    private final Supplier<? extends Iterable<? extends IECOConfigurableCellDrive>> drives;
    private final Predicate<Player> canConfigure;
    private final Map<UUID, Target> selected = new HashMap<>();

    /**
     * @param drives current drives belonging to this host; never look up arbitrary world positions
     * @param canConfigure checks host formation, same level, distance and player/network permissions
     */
    public ECOStorageCellPriorities(
        Supplier<? extends Iterable<? extends IECOConfigurableCellDrive>> drives, Predicate<Player> canConfigure) {
        this.drives = drives;
        this.canConfigure = canConfigure;
    }

    @Nullable
    public IECOConfigurableCellDrive resolve(Player player, @Nullable Target target) {
        if (target == null || target.position() == null || target.installation() == null
            || !canConfigure.test(player)) return null;
        for (var drive : drives.get()) {
            if (drive.isRemoved() || !target.position().equals(drive.getBlockPos())
                || !target.installation().equals(drive.getCellInteractionId()) || !drive.canExtractCell()) continue;
            var stack = drive.getCellStack();
            if (stack != null && ECOStorageCellSettings.canConfigure(stack)) return drive;
        }
        return null;
    }

    public int getPriority(Player player, @Nullable Target target) {
        var drive = resolve(player, target);
        return drive == null ? 0 : drive.getConfiguredCellPriority();
    }

    public int getSelectedPriority(Player player) {
        return getPriority(player, selected.get(player.getUUID()));
    }

    @Nullable
    public Target getSelectedTarget(Player player) {
        return selected.get(player.getUUID());
    }

    public void select(Player player, Target target) {
        if (player.level().isClientSide()) return;
        if (resolve(player, target) != null) selected.put(player.getUUID(), target);
        else selected.remove(player.getUUID());
    }

    public void setPriority(Player player, @Nullable Target target, int priority) {
        if (player.level().isClientSide()) return;
        var drive = resolve(player, target);
        if (drive == null) return;
        selected.put(player.getUUID(), target);
        drive.setConfiguredCellPriority(priority);
    }

    public void changePriority(Player player, @Nullable Target target, int delta) {
        if (player.level().isClientSide()) return;
        var drive = resolve(player, target);
        if (drive == null) return;
        selected.put(player.getUUID(), target);
        drive.setConfiguredCellPriority(StoragePriority.adjust(drive.getConfiguredCellPriority(), delta));
    }

    /** Forget a player's selection when the host/menu lifecycle no longer needs it. */
    public void clear(Player player) {
        selected.remove(player.getUUID());
    }
}
