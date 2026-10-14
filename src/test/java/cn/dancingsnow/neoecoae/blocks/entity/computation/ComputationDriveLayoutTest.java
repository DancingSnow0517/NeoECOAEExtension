package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.gui.computation.ComputationDriveEntry;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComputationDriveLayoutTest {
    private static final BlockPos CONTROLLER = new BlockPos(0, 64, 0);
    private static final int SLOTS = 11;

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void nearestDrivesFillTheLeadingSlotsWhateverTheInputOrder() {
        List<ComputationDriveEntry> entries = ComputationDriveLayout.entries(
            List.of(drive(at(3), null), drive(at(0), null), drive(at(2), null), drive(at(1), null)),
            CONTROLLER, false);
        assertEquals(SLOTS, entries.size());
        for (int i = 0; i < 4; i++) assertEquals(at(i), entries.get(i).position());
        for (int i = 4; i < SLOTS; i++) assertEmpty(entries.get(i));
    }

    @Test
    void mirroredLayoutFillsTheTrailingSlotsNearestFirst() {
        List<ComputationDriveEntry> entries = ComputationDriveLayout.entries(
            List.of(drive(at(3), null), drive(at(0), null), drive(at(2), null), drive(at(1), null)),
            CONTROLLER, true);
        assertEquals(SLOTS, entries.size());
        assertEquals(at(0), entries.get(10).position());
        assertEquals(at(1), entries.get(9).position());
        assertEquals(at(2), entries.get(8).position());
        assertEquals(at(3), entries.get(7).position());
        for (int i = 0; i < 7; i++) assertEmpty(entries.get(i));
    }

    @Test
    void onlyTheElevenNearestDrivesAreKept() {
        List<ECOComputationDriveBlockEntity> drives = new ArrayList<>();
        for (int i = 12; i >= 0; i--) drives.add(drive(at(i), null));
        List<ComputationDriveEntry> entries = ComputationDriveLayout.entries(drives, CONTROLLER, false);
        assertEquals(SLOTS, entries.size());
        for (int i = 0; i < SLOTS; i++) assertEquals(at(i), entries.get(i).position());
    }

    @Test
    void installedCellIsExposedAndAnEmptyDriveKeepsItsPosition() {
        ItemStack cell = new ItemStack(Items.DIAMOND);
        List<ComputationDriveEntry> entries = ComputationDriveLayout.entries(
            List.of(drive(at(1), null), drive(at(0), cell)), CONTROLLER, false);
        assertEquals(SLOTS, entries.size());
        assertTrue(ItemStack.matches(cell, entries.get(0).stack()));
        assertEquals(at(0), entries.get(0).position());
        assertEquals(at(1), entries.get(1).position());
        assertTrue(entries.get(1).stack().isEmpty());
        for (int i = 2; i < SLOTS; i++) assertEmpty(entries.get(i));
    }

    @Test
    void nullOrEmptyRowYieldsElevenEmptyEntries() {
        List<ComputationDriveEntry> fromNull = ComputationDriveLayout.entries(null, CONTROLLER, false);
        List<ComputationDriveEntry> fromEmpty = ComputationDriveLayout.entries(List.of(), CONTROLLER, true);
        assertEquals(SLOTS, fromNull.size());
        assertEquals(SLOTS, fromEmpty.size());
        fromNull.forEach(ComputationDriveLayoutTest::assertEmpty);
        fromEmpty.forEach(ComputationDriveLayoutTest::assertEmpty);
    }

    private static ECOComputationDriveBlockEntity drive(BlockPos position, ItemStack cell) {
        ECOComputationDriveBlockEntity drive = mock(ECOComputationDriveBlockEntity.class);
        when(drive.getBlockPos()).thenReturn(position);
        when(drive.getCellStack()).thenReturn(cell);
        return drive;
    }

    private static BlockPos at(int i) {
        return CONTROLLER.relative(Direction.EAST, i + 2);
    }

    private static void assertEmpty(ComputationDriveEntry entry) {
        assertNull(entry.position());
        assertTrue(entry.stack().isEmpty());
    }
}
