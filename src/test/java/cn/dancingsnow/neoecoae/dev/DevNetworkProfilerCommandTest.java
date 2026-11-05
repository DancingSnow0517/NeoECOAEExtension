package cn.dancingsnow.neoecoae.dev;

import appeng.api.implementations.blockentities.IChestOrDrive;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static net.minecraft.commands.Commands.literal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevNetworkProfilerCommandTest {
    @Test void acceptsTimedLookTargetOrExplicitPositionAndZeroBasedSlot() {
        var dispatcher = dispatcher();
        for (String command : new String[] {"neodev start 60", "neodev start 1 ~ ~ ~", "neodev start 600 100 64 200 0",
                "neodev stop", "neodev status"}) {
            var parsed = dispatcher.parse(command, mock(CommandSourceStack.class));
            assertFalse(parsed.getReader().canRead(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
        }
    }

    @Test void rejectsMissingOrUnboundedTimeAndInvalidSlot() {
        var dispatcher = dispatcher();
        for (String command : new String[] {"neodev start", "neodev start 0", "neodev start 601", "neodev start 60 1 2 3 -1",
                "neodev start 60 1 2 3 64"}) {
            var parsed = dispatcher.parse(command, mock(CommandSourceStack.class));
            assertTrue(parsed.getReader().canRead() || parsed.getContext().getCommand() == null, command);
        }
    }

    @Test void findsTheOnlyInstalledToolInsteadOfAssumingSlotZeroAndRejectsAmbiguity() {
        var drive = mock(IChestOrDrive.class);
        when(drive.getCellCount()).thenReturn(10);
        when(drive.getOriginalCellInventory(4)).thenReturn(new GeneratorGridService.GeneratorCell(ItemStack.EMPTY));
        assertEquals(4, DevNetworkProfiler.selectSlot(drive, null));
        assertEquals(4, DevNetworkProfiler.selectSlot(drive, 4));
        assertThrows(IllegalArgumentException.class, () -> DevNetworkProfiler.selectSlot(drive, 0));
        assertThrows(IllegalArgumentException.class, () -> DevNetworkProfiler.selectSlot(drive, 10));
        when(drive.getOriginalCellInventory(7)).thenReturn(new GeneratorGridService.GeneratorCell(ItemStack.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> DevNetworkProfiler.selectSlot(drive, null));
        assertEquals(7, DevNetworkProfiler.selectSlot(drive, 7));
    }

    @Test void workloadFingerprintIsStableForCompoundsButDetectsPaletteOrderAndSettingsChanges() {
        var first = new CompoundTag();
        var keys = new ListTag();
        keys.add(StringTag.valueOf("ae2:i/stone"));
        keys.add(StringTag.valueOf("ae2:f/water"));
        first.put("keys", keys);
        first.putLong("minimum", 10);
        first.putLong("maximum", 100);
        var reordered = new CompoundTag();
        reordered.putLong("maximum", 100);
        reordered.putLong("minimum", 10);
        reordered.put("keys", keys.copy());
        String fingerprint = DevNetworkProfiler.workloadFingerprint(first);
        assertEquals(64, fingerprint.length());
        assertEquals(fingerprint, DevNetworkProfiler.workloadFingerprint(reordered));
        reordered.putLong("minimum", 11);
        assertNotEquals(fingerprint, DevNetworkProfiler.workloadFingerprint(reordered));
        reordered.putLong("minimum", 10);
        var reverse = new ListTag();
        reverse.add(keys.get(1).copy());
        reverse.add(keys.get(0).copy());
        reordered.put("keys", reverse);
        assertNotEquals(fingerprint, DevNetworkProfiler.workloadFingerprint(reordered));
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var root = literal("neodev");
        DevNetworkProfiler.commands(root);
        dispatcher.register(root);
        return dispatcher;
    }
}
