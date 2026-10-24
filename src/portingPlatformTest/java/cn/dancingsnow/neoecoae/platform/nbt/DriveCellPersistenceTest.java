package cn.dancingsnow.neoecoae.platform.nbt;

import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DriveCellPersistenceTest {
    @BeforeAll static void bootstrap() { PlatformTestBootstrap.initialize(); }

    @Test void missingOptionalItemRetainsItsExactRegistryNameAndNbt() {
        CompoundTag disk = new ItemStack(Items.PAPER).save(new CompoundTag());
        disk.putString("id", "absent:quantum_disk");
        CompoundTag contents = new CompoundTag(); contents.putString("ae_universal_cell_uuid", "retained-identity");
        disk.put("tag", contents);
        CompoundTag drive = new CompoundTag(); drive.put("cellStack", disk);
        assertTrue(ItemStack.of(disk).isEmpty());
        CompoundTag kept = DriveCellPersistence.unresolved(drive, ItemStack.of(disk));
        assertEquals(disk, kept);
        kept.putString("id", "changed:test");
        assertEquals("absent:quantum_disk", disk.getString("id"));
    }

    @Test void aLaterSaveCannotOverwriteEscrowWithAnEmptyDecodedStack() {
        CompoundTag kept = new CompoundTag(); kept.putString("id", "absent:disk"); kept.putByte("Count", (byte) 1);
        CompoundTag drive = new CompoundTag(); drive.put("unresolvedCellStack", kept);
        drive.put("cellStack", ItemStack.EMPTY.save(new CompoundTag()));
        assertEquals(kept, DriveCellPersistence.unresolved(drive, ItemStack.EMPTY));
    }

    @Test void realEmptyAndNormallyDecodedSlotsDoNotBecomeQuarantined() {
        assertTrue(DriveCellPersistence.unresolved(new CompoundTag(), null).isEmpty());
        CompoundTag normal = new CompoundTag(); normal.put("cellStack", new ItemStack(Items.PAPER).save(new CompoundTag()));
        assertTrue(DriveCellPersistence.unresolved(normal, new ItemStack(Items.PAPER)).isEmpty());
        CompoundTag air = new CompoundTag(); air.put("cellStack", ItemStack.EMPTY.save(new CompoundTag()));
        assertTrue(DriveCellPersistence.unresolved(air, ItemStack.EMPTY).isEmpty());
    }
}
