package cn.dancingsnow.neoecoae.api.storage;

import cn.dancingsnow.neoecoae.api.storage.ECOStorageCellPriorities.Target;
import cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOStorageCellPrioritiesTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void independentAddonDriveUsesSharedSelectionAndSignedPriorityAdjustments() {
        var fixture = new Fixture();
        var target = fixture.target();
        fixture.access.select(fixture.first, target);
        fixture.access.setPriority(fixture.first, target, -42);
        assertEquals(-42, fixture.access.getSelectedPriority(fixture.first));
        assertEquals(0, fixture.access.getSelectedPriority(fixture.second));
        fixture.access.changePriority(fixture.first, target, Integer.MAX_VALUE);
        fixture.access.changePriority(fixture.first, target, 100);
        assertEquals(Integer.MAX_VALUE, fixture.drive.priority);
        fixture.access.setPriority(fixture.first, target, Integer.MIN_VALUE);
        fixture.access.changePriority(fixture.first, target, -100);
        assertEquals(Integer.MIN_VALUE, fixture.drive.priority);
        fixture.access.clear(fixture.first);
        assertNull(fixture.access.getSelectedTarget(fixture.first));
    }

    @Test
    void targetCannotEscapeHostMembershipOrSurviveCellReplacement() {
        var fixture = new Fixture();
        var original = fixture.target();
        fixture.access.select(fixture.first, original);
        fixture.drive.installation = UUID.randomUUID();
        fixture.access.setPriority(fixture.first, original, 100);
        fixture.access.changePriority(fixture.first, original, 100);
        assertEquals(0, fixture.drive.writes);
        assertEquals(0, fixture.access.getSelectedPriority(fixture.first));
        fixture.drives.clear();
        fixture.access.setPriority(fixture.first, fixture.target(), 100);
        assertEquals(0, fixture.drive.writes);
        fixture.access.select(fixture.first, original);
        assertNull(fixture.access.getSelectedTarget(fixture.first));
    }

    @Test
    void authorizationRemovalAndMigrationAreRecheckedForEveryReadAndWrite() {
        var fixture = new Fixture();
        var target = fixture.target();
        fixture.access.setPriority(fixture.first, target, 10);
        fixture.allowed = false;
        fixture.assertBlocked(target);
        fixture.allowed = true;
        fixture.drive.removed = true;
        fixture.assertBlocked(target);
        fixture.drive.removed = false;
        fixture.drive.extractable = false;
        fixture.assertBlocked(target);
        fixture.drive.extractable = true;
        cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember.beginMigration(fixture.drive.stack, UUID.randomUUID());
        fixture.assertBlocked(target);
        cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember.markMember(fixture.drive.stack, UUID.randomUUID());
        fixture.assertBlocked(target);
        fixture.drive.stack = ItemStack.EMPTY;
        fixture.assertBlocked(target);
        fixture.drive.stack = null;
        fixture.assertBlocked(target);
        assertEquals(1, fixture.drive.writes);
    }

    @Test
    void clientCanReadButCannotMutateOrSelect() throws Exception {
        var fixture = new Fixture();
        var level = fixture.first.level();
        when(level.isClientSide()).thenReturn(true);
        fixture.drive.priority = 7;
        assertEquals(7, fixture.access.getPriority(fixture.first, fixture.target()));
        fixture.access.select(fixture.first, fixture.target());
        fixture.access.setPriority(fixture.first, fixture.target(), 10);
        fixture.access.changePriority(fixture.first, fixture.target(), 10);
        assertNull(fixture.access.getSelectedTarget(fixture.first));
        assertEquals(0, fixture.drive.writes);
    }

    private static Player player() {
        var player = mock(Player.class);
        when(player.level()).thenReturn(mock(Level.class));
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static final class Fixture {
        final Player first = player();
        final Player second = player();
        final AddonDrive drive = new AddonDrive();
        final List<IECOConfigurableCellDrive> drives = new ArrayList<>(List.of(drive));
        boolean allowed = true;
        final ECOStorageCellPriorities access = new ECOStorageCellPriorities(() -> drives, player -> allowed);

        Target target() { return new Target(drive.getBlockPos(), drive.installation); }

        void assertBlocked(Target target) {
            int previous = drive.writes;
            assertNull(access.resolve(first, target));
            assertEquals(0, access.getPriority(first, target));
            access.setPriority(first, target, 99);
            access.changePriority(first, target, 99);
            assertEquals(previous, drive.writes);
        }
    }

    /** Deliberately has no inheritance relationship with either built-in block entity. */
    private static final class AddonDrive implements IECOConfigurableCellDrive {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        UUID installation = UUID.randomUUID();
        boolean extractable = true;
        boolean removed;
        int priority;
        int writes;

        @Override public BlockPos getBlockPos() { return BlockPos.ZERO; }
        @Override public boolean isRemoved() { return removed; }
        @Override public ItemStack getCellStack() { return stack; }
        @Override public UUID getCellInteractionId() { return installation; }
        @Override public boolean canExtractCell() { return extractable; }
        @Override public int getConfiguredCellPriority() { return priority; }
        @Override public void setConfiguredCellPriority(int priority) {
            this.priority = priority;
            ECOStorageCellSettings.setPriority(stack, priority);
            writes++;
        }
    }
}
