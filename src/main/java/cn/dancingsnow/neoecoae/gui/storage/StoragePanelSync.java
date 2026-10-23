package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import java.util.function.Supplier;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTextureState;
import net.minecraft.world.level.block.state.BlockState;

/** One display snapshot and one bounded page request, registered identically on both sides. */
final class StoragePanelSync {
    private final Supplier<StorageHostUI.Config> config;
    private final int pageSize;
    private final Supplier<BlockState> hostState;
    private final BindableValue<Integer> pageRequest = new BindableValue<>(0);
    private int requestedOffset;
    private HostPanelTextureState textures = HostPanelTextureState.EMPTY;
    private StoragePanelState state = StoragePanelState.EMPTY;

    StoragePanelSync(UIElement parent, Supplier<StorageHostUI.Config> config, int pageSize) {
        this(parent, config, pageSize, null);
    }

    StoragePanelSync(UIElement parent, Supplier<StorageHostUI.Config> config, int pageSize, Supplier<BlockState> hostState) {
        this.config = config;
        this.hostState = hostState;
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
        // Keep the old item-preview factory compatible; live host factories always supply placed state.
        textures = hostState == null ? HostPanelTextureState.capture(config.get().hostItem().get())
            : HostPanelTextureState.capture(hostState.get());
        CompoundTag tag = state.toTag(config.get().registries());
        textures.write(tag);
        return tag;
    }
}
