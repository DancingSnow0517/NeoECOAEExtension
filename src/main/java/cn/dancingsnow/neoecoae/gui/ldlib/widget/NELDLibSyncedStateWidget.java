package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public abstract class NELDLibSyncedStateWidget<S> extends NELDLibMachineWidget {
    private static final int STATE_UPDATE_ID = 1;

    private final Supplier<S> stateSupplier;
    private final BiConsumer<FriendlyByteBuf, S> encoder;
    private final Function<FriendlyByteBuf, S> decoder;
    private final int syncIntervalTicks;

    private S currentState;
    private S lastSentState;
    private int ticks;

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

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        ticks++;
        if (ticks == 1 || ticks % syncIntervalTicks == 0) {
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
