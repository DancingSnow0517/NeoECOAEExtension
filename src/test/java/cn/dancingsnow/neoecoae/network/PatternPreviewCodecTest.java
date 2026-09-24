package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewCodec;
import cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewEntry;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatternPreviewCodecTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void emptyCapacityUsesOneRunAndRoundTripsAllPhysicalAddresses() {
        CompoundTag payload = payload(256);
        var entries = new ListTag();
        for (int i = 0; i < 256; i++) entries.add(encoded(i, ItemStack.EMPTY, List.of(), false));
        payload.put("entries", entries);
        var compact = buffer();
        var legacy = buffer();
        try {
            PatternPreviewCodec.write(compact, payload);
            legacy.writeNbt(payload);
            assertTrue(compact.readableBytes() < 64);
            assertTrue(compact.readableBytes() * 100 < legacy.readableBytes());
            System.out.println("Preview empty page bytes: compact=" + compact.readableBytes() + ", legacy=" + legacy.readableBytes());
            var decoded = PatternPreviewCodec.read(compact).getList("entries", Tag.TAG_COMPOUND);
            assertEquals(256, decoded.size());
            for (int i = 0; i < 256; i++) {
                assertEquals(i, decoded.getCompound(i).getInt("index"));
                assertEquals(i, decoded.getCompound(i).getInt("slot"));
                assertEquals(42, decoded.getCompound(i).getLong("bus"));
            }
            assertFalse(compact.isReadable());
        } finally { compact.release(); legacy.release(); }
    }

    @Test void sparseChangesAndDiskContentsKeepFlagsStacksAndAddresses() {
        var payload = payload(20);
        payload.putBoolean("full", false);
        var entries = new ListTag();
        entries.add(encoded(2, new ItemStack(Items.STONE), List.of(), false));
        entries.add(encoded(19, ItemStack.EMPTY, List.of(new PatternPreviewEntry.DiskPattern(
                new ItemStack(Items.DIAMOND), "diamond", (byte) 3)), true));
        payload.put("entries", entries);
        var buf = buffer();
        try {
            PatternPreviewCodec.write(buf, payload);
            var decoded = PatternPreviewCodec.read(buf);
            assertFalse(decoded.getBoolean("full"));
            var result = decoded.getList("entries", Tag.TAG_COMPOUND);
            assertEquals(2, result.size());
            assertEquals(19, result.getCompound(1).getInt("index"));
            var disk = PatternPreviewEntry.decode(result.getCompound(1), RegistryAccess.EMPTY);
            assertTrue(disk.auxiliaryDisk());
            assertEquals(1, disk.diskPatterns().size());
            assertEquals(3, disk.diskPatterns().getFirst().flags());
            assertTrue(disk.diskPatterns().getFirst().stack().is(Items.DIAMOND));
        } finally { buf.release(); }
    }

    @Test void rejectsOversizedCatalogueBeforeAllocatingEntries() {
        var buf = buffer();
        try {
            buf.writeVarInt(1); buf.writeVarInt(1); buf.writeVarInt(-1);
            buf.writeVarInt(PatternPreviewCodec.MAX_SLOTS + 1);
            assertThrows(IllegalArgumentException.class, () -> PatternPreviewCodec.read(buf));
        } finally { buf.release(); }
    }

    private static CompoundTag payload(int size) {
        var result = new CompoundTag();
        result.putInt("menu", 1); result.putInt("revision", 1); result.putInt("base", -1);
        result.putInt("size", size); result.putBoolean("full", true);
        result.putBoolean("first", true); result.putBoolean("last", true);
        return result;
    }

    private static CompoundTag encoded(int slot, ItemStack stack, List<PatternPreviewEntry.DiskPattern> disk, boolean readOnly) {
        var result = new PatternPreviewEntry(42, slot, stack, "", (byte) 0, disk, readOnly).encode(RegistryAccess.EMPTY);
        result.putInt("index", slot);
        return result;
    }

    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }
}
