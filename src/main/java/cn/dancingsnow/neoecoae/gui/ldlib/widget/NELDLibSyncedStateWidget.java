package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.network.BoundedData;
import cn.dancingsnow.neoecoae.network.StateDelta;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
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
    private UUID syncSession = UUID.randomUUID();
    private byte[] baseline = new byte[0];
    private byte[] pendingState;
    private byte[] pendingDelta;
    private int pendingOffset;
    private final BoundedData.Receiver receiver = new BoundedData.Receiver();
    private boolean failed;
    private boolean urgent;
    private long lastSyncTick = Long.MIN_VALUE;

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
        // Coalesce actions into the next tick; never bypass the per-tick fragment budget.
        urgent = true;
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        syncSession = UUID.randomUUID();
        baseline = new byte[0];
        pendingState = null;
        pendingDelta = null;
        pendingOffset = 0;
        failed = false;
        lastSentState = null;
        S state = stateSupplier.get();
        if (state != null) {
            currentState = state;
        }
        // Small initial states preserve child initialization semantics. Large states stream after opening.
        try {
            byte[] encoded = BoundedData.encode(StateDelta.MAX_STATE_BYTES, buf -> encoder.accept(buf, currentState));
            lastSentState = currentState;
            if (encoded.length <= 4096) baseline = encoded;
            else {
                pendingState = encoded;
                pendingDelta = StateDelta.encode(baseline, encoded);
            }
        } catch (RuntimeException tooLarge) {
            failed = true;
        }
        buffer.writeUUID(syncSession);
        buffer.writeBoolean(failed);
        buffer.writeByteArray(baseline);
        urgent = true;
        super.writeInitialData(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        syncSession = buffer.readUUID();
        failed = buffer.readBoolean();
        baseline = buffer.readByteArray(4096);
        receiver.clear();
        if (baseline.length > 0) {
            var initial = new FriendlyByteBuf(Unpooled.wrappedBuffer(baseline));
            try {
                currentState = decoder.apply(initial);
                if (initial.isReadable()) throw new IllegalArgumentException("Trailing initial UI bytes");
            } finally {
                initial.release();
            }
        }
        if (failed) cn.dancingsnow.neoecoae.network.MenuDataSync.notifyClientError();
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
        if (getGui() != null && getGui().entityPlayer instanceof net.minecraft.server.level.ServerPlayer player) {
            long tick = player.serverLevel().getGameTime();
            if (tick == lastSyncTick) return;
            lastSyncTick = tick;
        }
        ticks++;
        long revision = stateRevision();
        boolean revisionChanged = revision != NO_REVISION && revision != lastRevision;
        lastRevision = revision;
        urgent |= revisionChanged;
        if (!failed && pendingDelta == null && (ticks == 1 || urgent || ticks % syncIntervalTicks == 0)) {
            urgent = false;
            S state = stateSupplier.get();
            if (state != null && !Objects.equals(state, lastSentState)) {
                lastSentState = state;
                currentState = state;
                try {
                    byte[] encoded = BoundedData.encode(StateDelta.MAX_STATE_BYTES, buf -> encoder.accept(buf, state));
                    if (!Arrays.equals(baseline, encoded)) {
                        pendingState = encoded;
                        pendingDelta = StateDelta.encode(baseline, encoded);
                        pendingOffset = 0;
                    }
                } catch (RuntimeException tooLarge) {
                    failed = true;
                    writeUpdateInfo(STATE_UPDATE_ID, buf -> {
                        buf.writeUUID(syncSession);
                        buf.writeVarInt(-1);
                    });
                }
            }
        }
        if (pendingDelta != null) {
            int start = pendingOffset;
            int total = pendingDelta.length;
            int end = Math.min(total, start + BoundedData.PART_BYTES);
            byte[] part = Arrays.copyOfRange(pendingDelta, start, end);
            writeUpdateInfo(STATE_UPDATE_ID, buf -> {
                buf.writeUUID(syncSession);
                buf.writeVarInt(total);
                buf.writeVarInt(start);
                buf.writeByteArray(part);
            });
            pendingOffset = end;
            if (end == total) {
                baseline = pendingState;
                pendingState = null;
                pendingDelta = null;
            }
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == STATE_UPDATE_ID) {
            UUID session = buffer.readUUID();
            if (!syncSession.equals(session)) return;
            int total = buffer.readVarInt();
            if (total == -1) {
                receiver.clear();
                cn.dancingsnow.neoecoae.network.MenuDataSync.notifyClientError();
                return;
            }
            byte[] delta = receiver.accept(total, buffer.readVarInt(), buffer.readByteArray(BoundedData.PART_BYTES));
            if (delta != null) {
                byte[] next = StateDelta.apply(baseline, delta);
                var data = new FriendlyByteBuf(Unpooled.wrappedBuffer(next));
                try {
                    S state = decoder.apply(data);
                    if (data.isReadable()) throw new IllegalArgumentException("Trailing UI state bytes");
                    currentState = state;
                    baseline = next;
                } finally {
                    data.release();
                }
            }
            return;
        }
        super.readUpdateInfo(id, buffer);
    }
}
