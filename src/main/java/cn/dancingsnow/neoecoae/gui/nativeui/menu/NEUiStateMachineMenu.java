package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base for machine menus that periodically push a read-only S2C UI
 * state snapshot to the client.
 * <p>
 * Subclasses only need to implement {@link #createState(ServerPlayer)} and
 * {@link #sendState(ServerPlayer, Object)}. The base class handles the
 * tick counter, interval check (default 20 ticks), and duplicate-state
 * suppression.
 * </p>
 *
 * @param <S> the UI state record type (e.g. {@code NEStorageUiState})
 */
public abstract class NEUiStateMachineMenu<S> extends NEBaseMachineMenu {

    private int tickCounter;
    @Nullable
    private S lastSentState;
    private long lastSentRevision = Long.MIN_VALUE;

    protected NEUiStateMachineMenu(@Nullable MenuType<?> type, int containerId,
                                    Inventory playerInv, BlockPos machinePos) {
        super(type, containerId, playerInv, machinePos);
    }

    /**
     * How often (in ticks) the state should be re-evaluated and potentially
     * sent to the client. Default is 20 (once per second).
     */
    protected int getStateSyncIntervalTicks() {
        return 20;
    }

    /**
     * Optional lightweight revision. Return {@link Long#MIN_VALUE} to keep the
     * legacy behavior of creating a state snapshot every sync interval.
     */
    protected long getStateRevision(ServerPlayer player) {
        return Long.MIN_VALUE;
    }

    /**
     * Creates a current UI state snapshot from the server-side world.
     * Called on the server thread. May return {@code null} to skip this
     * tick (e.g. when the target block entity is missing or unformed).
     */
    @Nullable
    protected abstract S createState(ServerPlayer player);

    /**
     * Sends the state snapshot to a specific player via the mod network
     * channel. Called after {@link #createState} returns a non-null,
     * non-duplicate state.
     */
    protected abstract void sendState(ServerPlayer player, S state);

    @Override
    public final void broadcastChanges() {
        super.broadcastChanges();

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        tickCounter++;

        if (tickCounter == 1 || tickCounter % getStateSyncIntervalTicks() == 0) {
            long revision = getStateRevision(serverPlayer);
            boolean revisionEnabled = revision != Long.MIN_VALUE;
            if (tickCounter != 1 && revisionEnabled && revision == lastSentRevision) {
                return;
            }
            S state = createState(serverPlayer);
            if (state == null) {
                return;
            }
            // Suppress duplicate sends when nothing changed (skip on first tick)
            if (tickCounter != 1 && state.equals(lastSentState)) {
                return;
            }
            lastSentState = state;
            if (revisionEnabled) {
                lastSentRevision = revision;
            }
            sendState(serverPlayer, state);
        }
    }

    /**
     * Immediately creates and sends the current UI state to the given player,
     * bypassing the interval timer and duplicate suppression. Useful after a
     * C2S action that modifies the machine state and needs instant UI feedback.
     */
    public void sendStateNow(ServerPlayer player) {
        S state = createState(player);
        if (state != null) {
            lastSentState = state;
            long revision = getStateRevision(player);
            if (revision != Long.MIN_VALUE) {
                lastSentRevision = revision;
            }
            sendState(player, state);
        }
    }
}
