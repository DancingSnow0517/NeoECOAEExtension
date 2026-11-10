package cn.dancingsnow.neoecoae.menu;

import cn.dancingsnow.neoecoae.api.storage.IECOCellWorkbenchDrive;
import cn.dancingsnow.neoecoae.api.storage.IECOConfigurableCellHost;
import cn.dancingsnow.neoecoae.blocks.entity.storage.StorageCellWorkbench;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageCellWorkbenchLocatorTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void independentAddonHostAndDriveReachTheSharedWorkbench() {
        var fixture = new Fixture();
        // Construction of AE2's registered block entity belongs to the game lifecycle.
        try (var workbenches = mockConstruction(StorageCellWorkbench.class, (workbench, context) -> {
            assertSame(fixture.host, context.arguments().get(0));
            assertEquals(fixture.drivePosition, context.arguments().get(1));
            assertEquals(fixture.installation, context.arguments().get(2));
            when(workbench.isValidFor(fixture.player)).thenReturn(true);
        })) {
            var result = fixture.locator.locate(fixture.player, StorageCellWorkbench.class);
            assertEquals(1, workbenches.constructed().size());
            assertSame(workbenches.constructed().getFirst(), result);
            verify(result).isValidFor(fixture.player);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"unformed", "denied", "removedHost", "otherLevel", "otherHost",
        "foreignDrive", "removedDrive", "replaced", "locked", "empty", "missing", "multiple", "migrating", "member"})
    void invalidOrStaleTargetNeverConstructsAWorkbench(String state) {
        var fixture = new Fixture();
        switch (state) {
            case "unformed" -> when(fixture.host.isFormed()).thenReturn(false);
            case "denied" -> when(fixture.host.canPlayerInteract(fixture.player)).thenReturn(false);
            case "removedHost" -> when(fixture.blockEntity.isRemoved()).thenReturn(true);
            case "otherLevel" -> when(fixture.blockEntity.getLevel()).thenReturn(mock(Level.class));
            case "otherHost" -> when(fixture.host.getCellWorkbenchBlockEntity()).thenReturn(mock(BlockEntity.class));
            case "foreignDrive" -> doReturn(List.of()).when(fixture.host).getStorageDrivesForIntegration();
            case "removedDrive" -> when(fixture.drive.isRemoved()).thenReturn(true);
            case "replaced" -> when(fixture.drive.getCellInteractionId()).thenReturn(UUID.randomUUID());
            case "locked" -> when(fixture.drive.canExtractCell()).thenReturn(false);
            case "empty" -> when(fixture.drive.getCellStack()).thenReturn(ItemStack.EMPTY);
            case "missing" -> when(fixture.drive.getCellStack()).thenReturn(null);
            case "multiple" -> fixture.stack.setCount(2);
            case "migrating" -> ECOInfiniteStorageMember.beginMigration(fixture.stack, UUID.randomUUID());
            case "member" -> ECOInfiniteStorageMember.markMember(fixture.stack, UUID.randomUUID());
            default -> throw new AssertionError(state);
        }
        try (var workbenches = mockConstruction(StorageCellWorkbench.class)) {
            assertNull(fixture.locator.locate(fixture.player, StorageCellWorkbench.class));
            assertTrue(workbenches.constructed().isEmpty());
        }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Player player = mock(Player.class);
        final BlockEntity blockEntity = mock(BlockEntity.class,
            withSettings().extraInterfaces(IECOConfigurableCellHost.class));
        final IECOConfigurableCellHost host = (IECOConfigurableCellHost) blockEntity;
        final IECOCellWorkbenchDrive drive = mock(IECOCellWorkbenchDrive.class);
        final BlockPos drivePosition = new BlockPos(1, 0, 0);
        final UUID installation = UUID.randomUUID();
        final ItemStack stack = new ItemStack(Items.DIAMOND);
        final StorageCellWorkbenchLocator locator = new StorageCellWorkbenchLocator(BlockPos.ZERO, drivePosition, installation);

        Fixture() {
            when(player.level()).thenReturn(level);
            when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(blockEntity);
            when(blockEntity.getLevel()).thenReturn(level);
            when(host.getCellWorkbenchBlockEntity()).thenReturn(blockEntity);
            when(host.isFormed()).thenReturn(true);
            when(host.canPlayerInteract(player)).thenReturn(true);
            doReturn(List.of(drive)).when(host).getStorageDrivesForIntegration();
            when(drive.getBlockPos()).thenReturn(drivePosition);
            when(drive.getCellInteractionId()).thenReturn(installation);
            when(drive.canExtractCell()).thenReturn(true);
            when(drive.getCellStack()).thenReturn(stack);
        }
    }
}
