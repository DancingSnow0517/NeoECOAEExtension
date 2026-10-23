package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

/** Server-owned artwork snapshot shared by a host's drawing and interactive widgets. */
public final class HostPanelTextureSync extends BindableValue<Tag> implements Supplier<HostPanelTextureState> {
    private HostPanelTextureState textures = HostPanelTextureState.EMPTY;

    public HostPanelTextureSync(Supplier<BlockState> hostState, HostPanelTextureState.Panel panel) {
        super(new CompoundTag());
        Supplier<Tag> snapshot = () -> {
            CompoundTag tag = new CompoundTag();
            HostPanelTextureState.capture(hostState.get(), panel).write(tag);
            return tag;
        };
        registerValueListener(value -> textures = value instanceof CompoundTag tag
            ? HostPanelTextureState.read(tag) : HostPanelTextureState.EMPTY);
        setValue(snapshot.get());
        bind(DataBindingBuilder.tagS2C(snapshot).build());
        setDisplay(false);
    }

    @Override
    public HostPanelTextureState get() {
        return textures;
    }

    public void onChange(Runnable listener) {
        registerValueListener(value -> listener.run());
    }
}
