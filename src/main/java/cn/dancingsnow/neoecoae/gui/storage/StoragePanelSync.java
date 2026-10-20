package cn.dancingsnow.neoecoae.gui.storage;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import java.util.function.Supplier;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTextureState;

/** One display snapshot and one bounded page request, registered identically on both sides. */
final class StoragePanelSync {
    private final Supplier<StorageHostUI.Config> config;
    private final int pageSize;
    private final BindableValue<Integer> pageRequest = new BindableValue<>(0);
    private int requestedOffset;
    private HostPanelTextureState textures = HostPanelTextureState.EMPTY;
    private StoragePanelState state = StoragePanelState.EMPTY;

    StoragePanelSync(UIElement parent, Supplier<StorageHostUI.Config> config, int pageSize) {
        this.config = config;
        this.pageSize = pageSize;
        Tag initial = snapshot();
        BindableValue<Tag> synced = new BindableValue<>(initial);
        synced.bind(DataBindingBuilder.tagS2C(this::snapshot).build());
        synced.registerValueListener(value -> {
            state = StoragePanelState.fromTag(value, config.get().registries());
            textures = value instanceof CompoundTag tag ? HostPanelTextureState.read(tag) : HostPanelTextureState.EMPTY;
        });
        synced.setDisplay(false);
        pageRequest.bind(DataBindingBuilder.intValC2S(value -> requestedOffset = Math.max(0, value)).build());
        pageRequest.setDisplay(false);
        parent.addChildren(synced, pageRequest);
    }

    HostPanelTextureState textures() { return textures; }

    StoragePanelState state() { return state; }

    void scroll(int delta) {
        int next = StoragePanelState.pageOffset(state.cellOffset() + delta * pageSize, state.cellCount(), pageSize);
        pageRequest.setValue(next);
    }

    private Tag snapshot() {
        state = StoragePanelState.capture(config.get(), requestedOffset, pageSize);
        requestedOffset = state.cellOffset();
        textures = HostPanelTextureState.capture(config.get().hostItem().get());
        CompoundTag tag = state.toTag(config.get().registries());
        textures.write(tag);
        return tag;
    }
}
