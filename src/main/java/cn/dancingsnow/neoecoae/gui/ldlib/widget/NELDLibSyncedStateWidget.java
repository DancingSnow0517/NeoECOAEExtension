package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public abstract class NELDLibSyncedStateWidget<S> extends NELDLibMachineWidget {
    /** Must stay clear of the ids {@link com.lowdragmc.lowdraglib.gui.widget.WidgetGroup} uses for its children. */
    protected static final int STATE_UPDATE_ID = FIRST_CUSTOM_UPDATE_ID;

    /** {@link #stateRevision()} opt-out: nothing to report, so only the interval decides. */
    protected static final long NO_REVISION = Long.MIN_VALUE;

    private final Supplier<S> stateSupplier;
    private final BiConsumer<FriendlyByteBuf, S> encoder;
    private final Function<FriendlyByteBuf, S> decoder;
    private final int syncIntervalTicks;

    private S currentState;
    private S lastSentState;
    private int ticks;
    private long lastRevision = NO_REVISION;

    protected NELDLibSyncedStateWidget(
            Component title,
            int width,
            int height,
            S emptyState,
            Supplier<S> stateSupplier,
            BiConsumer<FriendlyByteBuf, S> encoder,
            Function<FriendlyByteBuf, S> decoder,
            int syncIntervalTicks) {
        super(title, width, height);
        this.currentState = emptyState;
        this.stateSupplier = stateSupplier;
        this.encoder = encoder;
        this.decoder = decoder;
        this.syncIntervalTicks = Math.max(1, syncIntervalTicks);
    }

    protected S currentState() {
        return currentState;
    }

    protected void syncStateNow() {
        S state = stateSupplier.get();
        if (state == null) {
            return;
        }
        lastSentState = state;
        currentState = state;
        writeUpdateInfo(STATE_UPDATE_ID, buf -> encoder.accept(buf, state));
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        S state = stateSupplier.get();
        if (state != null) {
            currentState = state;
            lastSentState = state;
        }
        encoder.accept(buffer, currentState);
        super.writeInitialData(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        currentState = decoder.apply(buffer);
        super.readInitialData(buffer);
    }

    /**
     * Out-of-band sync trigger, opt-in per subclass. The interval keeps continuously drifting
     * numbers -- byte counts, elapsed job times -- from being encoded and shipped every tick, but it
     * delays changes the player just caused by up to a full interval, which reads as the UI ignoring
     * the interaction. A host that counts those rare configuration edits can return that counter
     * here to have them pushed on the very next tick instead.
     *
     * <p>Only return a counter that moves on player-visible edits. Wiring this to something that
     * also tracks per-tick machine churn would reinstate the per-tick encode the interval avoids.
     *
     * @return a value that changes when the state must be pushed promptly, or {@link #NO_REVISION}
     */
    protected long stateRevision() {
        return NO_REVISION;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        ticks++;
        long revision = stateRevision();
        boolean revisionChanged = revision != NO_REVISION && revision != lastRevision;
        lastRevision = revision;
        if (ticks == 1 || revisionChanged || ticks % syncIntervalTicks == 0) {
            S state = stateSupplier.get();
            if (state != null && !Objects.equals(state, lastSentState)) {
                lastSentState = state;
                currentState = state;
                writeUpdateInfo(STATE_UPDATE_ID, buf -> encoder.accept(buf, state));
            }
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == STATE_UPDATE_ID) {
            currentState = decoder.apply(buffer);
            return;
        }
        super.readUpdateInfo(id, buffer);
    }
}
