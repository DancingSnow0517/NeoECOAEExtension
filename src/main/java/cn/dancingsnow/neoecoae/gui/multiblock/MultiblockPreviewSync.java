package cn.dancingsnow.neoecoae.gui.multiblock;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Single compound S2C binding for all multiblock preview state. */
public final class MultiblockPreviewSync extends UIElement implements IBindable<CompoundTag> {
    private final Supplier<HolderLookup.Provider> registries;
    private final Supplier<MultiblockPreviewSnapshot> snapshot;
    private CompoundTag value = new CompoundTag();
    private MultiblockPreviewSnapshot decoded = new MultiblockPreviewSnapshot(
        MultiblockPreviewSnapshot.Status.NO_DEFINITION, 0, 0, 0, 0, List.of(), List.of());
    private final List<Consumer<MultiblockPreviewSnapshot>> subscribers = new ArrayList<>();

    public MultiblockPreviewSync(Player player, Supplier<MultiblockPreviewSnapshot> snapshot) {
        this(() -> player.level().registryAccess(), snapshot);
    }

    public MultiblockPreviewSync(Supplier<HolderLookup.Provider> registries, Supplier<MultiblockPreviewSnapshot> snapshot) {
        this.registries = registries;
        this.snapshot = snapshot;
        layout(layout -> layout.width(0).height(0));
        bind(DataBindingBuilder.create(() -> encode(snapshot.get(), registries.get()), ignored -> {
        }).syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
    }

    public MultiblockPreviewSnapshot snapshot() { return decoded; }
    public void subscribe(Consumer<MultiblockPreviewSnapshot> subscriber) { subscribers.add(subscriber); subscriber.accept(decoded); }

    @Override public CompoundTag getValue() { return value.copy(); }

    @Override public IDataSource<CompoundTag> setValue(@Nullable CompoundTag value) {
        this.value = value == null ? new CompoundTag() : value.copy();
        this.decoded = decode(this.value, registries.get());
        subscribers.forEach(subscriber -> subscriber.accept(decoded));
        return this;
    }

    public static CompoundTag encode(MultiblockPreviewSnapshot snapshot, HolderLookup.Provider registries) {
        return MultiblockPreviewCodec.encode(snapshot, registries);
    }

    static CompoundTag encodeBase(MultiblockPreviewSnapshot snapshot) {
        return MultiblockPreviewCodec.encodeBase(snapshot);
    }

    public static MultiblockPreviewSnapshot decode(@Nullable CompoundTag tag, HolderLookup.Provider registries) {
        return MultiblockPreviewCodec.decode(tag, registries);
    }

    static MultiblockPreviewSnapshot decodeBase(@Nullable CompoundTag tag) {
        return MultiblockPreviewCodec.decodeBase(tag);
    }
}
