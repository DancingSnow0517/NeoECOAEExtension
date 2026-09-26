package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
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

    public KeyCounter collect(MEStorageMenu menu, Supplier<KeyCounter> listing) {
        if (!(menu.getPlayer() instanceof ServerPlayer player)) return listing.get();
        // ServerPlayer.initMenu adds a slot listener, which calls broadcastChanges BEFORE
        // player.containerMenu is assigned. The transport would bind that snapshot to the
        // old menu and discard it. Do not advance either the baseline or throttle until
        // this menu is active, so its first ordinary tick still sends a full snapshot.
        if (player.containerMenu != menu) return listing.get();
        long tick = player.level().getGameTime();
        if (tick < nextTick || MenuDataTransport.busy(player, MenuDataTransport.Channel.TERMINAL))
            return listing.get();
        nextTick = tick + MenuDataTransport.UPDATE_INTERVAL;
        ExactAmountCollector.begin();
        try {
            KeyCounter result = listing.get();
            Map<AEKey, ExactAmount> current = ExactAmountCollector.finish();
            boolean full = previous == null;
            var delta = MapDelta.between(full ? Map.of() : previous, current);
            if (full || !delta.isEmpty()) {
                MenuDataTransport.send(player, MenuDataTransport.Channel.TERMINAL, buf -> {
                    buf.writeBoolean(full);
                    ExactMapSync.write(buf, delta);
                });
                previous = current;
            }
            return result;
        } catch (RuntimeException | Error failure) {
            ExactAmountCollector.abort();
            throw failure;
        }
    }
}
