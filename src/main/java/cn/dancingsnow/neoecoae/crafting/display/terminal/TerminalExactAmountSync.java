package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.network.ExactMapSync;
import cn.dancingsnow.neoecoae.network.MapDelta;
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;

/** Per-menu exact-amount synchronization, sharing the terminal's normal inventory listing. */
public final class TerminalExactAmountSync {
    private Map<AEKey, ExactAmount> previous;
    private long nextTick;
    private long lastTick = Long.MIN_VALUE;
    private Object source;

    public KeyCounter collect(MEStorageMenu menu, Supplier<KeyCounter> listing) {
        return collect(menu, null, listing);
    }

    public KeyCounter collect(MEStorageMenu menu, MEStorage storage, Supplier<KeyCounter> listing) {
        if (!(menu.getPlayer() instanceof ServerPlayer player)) return listing.get();
        // ServerPlayer.initMenu adds a slot listener, which calls broadcastChanges BEFORE
        // player.containerMenu is assigned. The transport would bind that snapshot to the
        // old menu and discard it. Do not advance either the baseline or throttle until
        // this menu is active, so its first ordinary tick still sends a full snapshot.
        if (player.containerMenu != menu) return listing.get();
        long tick = player.level().getGameTime();
        Object currentSource = storage == null ? this : TerminalInventorySnapshot.resolve(storage);
        if (source != currentSource || tick < lastTick) {
            if (source != null) MenuDataTransport.cancel(player, MenuDataTransport.Channel.TERMINAL);
            source = currentSource;
            previous = null;
            nextTick = tick;
        }
        lastTick = tick;
        boolean sync = tick >= nextTick && !MenuDataTransport.busy(player, MenuDataTransport.Channel.TERMINAL);
        var snapshot = TerminalInventorySnapshot.sample(currentSource, tick, sync, listing);
        if (!sync) return snapshot.inventory();
        Map<AEKey, ExactAmount> current = snapshot.amounts();
        if (current != previous) {
            boolean full = previous == null;
            var delta = MapDelta.between(full ? Map.of() : previous, current);
            if (full || !delta.isEmpty()) {
                MenuDataTransport.send(player, MenuDataTransport.Channel.TERMINAL, buf -> {
                    buf.writeBoolean(full);
                    ExactMapSync.write(buf, delta);
                });
            }
            previous = current;
        }
        nextTick = tick + MenuDataTransport.UPDATE_INTERVAL;
        return snapshot.inventory();
    }
}
