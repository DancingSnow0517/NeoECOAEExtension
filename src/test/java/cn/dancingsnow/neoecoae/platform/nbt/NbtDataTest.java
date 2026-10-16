package cn.dancingsnow.neoecoae.platform.nbt;

import cn.dancingsnow.neoecoae.core.io.DataReader;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NbtDataTest {
    @Test void structuredPrimitiveFieldsKeepTheirNbtTypesAndMissingDefaults() throws Exception {
        var tag = new CompoundTag();
        var data = new NbtData(tag, mock(HolderLookup.Provider.class));
        data.writeInt("index", 3);
        data.writeLong("count", Long.MAX_VALUE);
        data.writeBoolean("enabled", true);
        data.writeIntArray("tasks", new int[]{1, 4});
        data.writeLongArray("offsets", new long[]{1L, Long.MAX_VALUE});
        data.writeCompound("nested", child -> child.writeInt("phase", 7));
        assertEquals(TagParser.parseTag("""
            {index:3,count:9223372036854775807L,enabled:1b,tasks:[I;1,4],
             offsets:[L;1L,9223372036854775807L],nested:{phase:7}}
            """), tag);
        assertEquals(3, data.readInt("index"));
        assertEquals(Long.MAX_VALUE, data.readLong("count"));
        assertTrue(data.readBoolean("enabled"));
        assertArrayEquals(new int[]{1, 4}, data.readIntArray("tasks"));
        assertArrayEquals(new long[]{1L, Long.MAX_VALUE}, data.readLongArray("offsets"));
        assertEquals(7, data.readCompound("nested").readInt("phase"));
        assertFalse(data.contains("missing"));
        assertEquals(0, data.readInt("missing"));
        assertEquals(0L, data.readLong("missing"));
        assertFalse(data.readBoolean("missing"));
        assertEquals("", data.readString("missing"));
        assertArrayEquals(new int[0], data.readIntArray("missing"));
        assertArrayEquals(new long[0], data.readLongArray("missing"));
        assertEquals(0, data.readCompound("missing").readInt("phase"));
    }

    @Test void compoundListsKeepExecutionAndExactAmountMalformedListPoliciesSeparate() {
        var tag = new CompoundTag();
        var malformed = new ListTag();
        malformed.add(IntTag.valueOf(1));
        tag.put("entries", malformed);
        DataReader<appeng.api.stacks.AEKey> data = new NbtData(tag, mock(HolderLookup.Provider.class));
        assertTrue(data.readCompoundList("entries").isEmpty(), "execution plans use CompoundTag.getList's typed fallback");
        assertEquals(1, data.readList("entries").size(), "exact amount maps still inspect and reject malformed elements");
        assertThrows(IllegalArgumentException.class, () -> data.readList("entries").getFirst().readKey("key"));
    }
}
