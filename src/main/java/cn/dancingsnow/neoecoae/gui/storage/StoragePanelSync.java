package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.HostElements;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import java.util.function.Supplier;
import net.minecraft.nbt.Tag;

/** One display snapshot and one bounded page request, registered identically on both sides. */
final class StoragePanelSync {
    private final Supplier<StorageHostUI.Config> config;
    private final int pageSize;
    private final BindableValue<Integer> pageRequest;
    private int requestedOffset;
    private StoragePanelState state = StoragePanelState.EMPTY;

    StoragePanelSync(UIElement parent, Supplier<StorageHostUI.Config> config, int pageSize) {
        this.config = config;
        this.pageSize = pageSize;
        Tag initial = snapshot();
        HostElements.hiddenValue(parent, initial, DataBindingBuilder.tagS2C(this::snapshot).build(),
            value -> state = StoragePanelState.fromTag(value, config.get().registries()));
        pageRequest = HostElements.hiddenValue(parent, 0,
            DataBindingBuilder.intValC2S(value -> requestedOffset = Math.max(0, value)).build());
    }

    StoragePanelState state() { return state; }

    void scroll(int delta) {
        int next = StoragePanelState.pageOffset(state.cellOffset() + delta * pageSize, state.cellCount(), pageSize);
        pageRequest.setValue(next);
    }

    private Tag snapshot() {
        state = StoragePanelState.capture(config.get(), requestedOffset, pageSize);
        requestedOffset = state.cellOffset();
        return state.toTag(config.get().registries());
    }
}
