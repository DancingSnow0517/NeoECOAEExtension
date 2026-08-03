package cn.dancingsnow.neoecoae.gui.crafting;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Structured S2C payload for per-host crafting batch information. */
final class CraftingHostBatchSyncElement extends UIElement implements IBindable<CompoundTag> {
    record HostBatchData(boolean highEnergy, int threads, long batch) {
    }

    private CompoundTag value = new CompoundTag();

    static CompoundTag encode(List<HostBatchData> hosts) {
        return CraftingHostBatchSyncCodec.encode(hosts);
    }

    static List<HostBatchData> decode(@Nullable CompoundTag payload) {
        return CraftingHostBatchSyncCodec.decode(payload);
    }

    @Override
    public CompoundTag getValue() {
        return value.copy();
    }

    @Override
    public IDataSource<CompoundTag> setValue(@Nullable CompoundTag value) {
        this.value = value == null ? new CompoundTag() : value.copy();
        return this;
    }
}
