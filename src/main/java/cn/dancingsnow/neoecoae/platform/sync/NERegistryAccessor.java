package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.accessor.CustomObjectAccessor;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import net.minecraft.core.Registry;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Supplier;

/** Defers custom-registry lookup until Forge has created and populated the registry. */
public final class NERegistryAccessor<T> extends CustomObjectAccessor<T> {
    private final Supplier<Registry<T>> registry;

    public NERegistryAccessor(Class<T> type, Supplier<Registry<T>> registry) {
        super(type, true);
        this.registry = registry;
    }

    @Override
    public ITypedPayload<?> serialize(AccessorOp op, T value) {
        var id = Objects.requireNonNull(registry.get().getKey(value), "Unregistered managed value");
        return NbtTagPayload.of(StringTag.valueOf(id.toString()));
    }

    @Override
    public T deserialize(AccessorOp op, ITypedPayload<?> payload) {
        if (!(payload instanceof NbtTagPayload nbt) || !(nbt.getPayload() instanceof StringTag tag)) {
            throw new IllegalArgumentException("Expected a managed registry ID");
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getAsString());
        if (id == null || !registry.get().containsKey(id)) {
            throw new IllegalArgumentException("Unknown managed registry ID: " + tag.getAsString());
        }
        return registry.get().get(id);
    }
}
