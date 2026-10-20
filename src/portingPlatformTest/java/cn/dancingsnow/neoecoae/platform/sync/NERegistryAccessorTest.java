package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Registry;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NERegistryAccessorTest {
    @Test
    @SuppressWarnings("unchecked")
    void resolvesTheRegistryLazilyAndRoundTripsRegisteredIds() {
        AtomicReference<Registry<String>> reference = new AtomicReference<>();
        var accessor = new NERegistryAccessor<>(String.class, reference::get);
        Registry<String> registry = mock(Registry.class);
        var id = new ResourceLocation("neoecoae", "l9");
        when(registry.getKey("value")).thenReturn(id);
        when(registry.containsKey(id)).thenReturn(true);
        when(registry.get(id)).thenReturn("value");
        reference.set(registry);
        var payload = accessor.serialize(AccessorOp.PERSISTED, "value");
        assertEquals("value", accessor.deserialize(AccessorOp.PERSISTED, payload));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsUnknownIdsAndIncorrectPayloadTypes() {
        Registry<String> registry = mock(Registry.class);
        var accessor = new NERegistryAccessor<>(String.class, () -> registry);
        assertThrows(IllegalArgumentException.class, () -> accessor.deserialize(AccessorOp.SYNCED,
            NbtTagPayload.of(StringTag.valueOf("neoecoae:missing"))));
        assertThrows(IllegalArgumentException.class, () -> accessor.deserialize(AccessorOp.SYNCED,
            NbtTagPayload.of(IntTag.valueOf(3))));
    }
}
