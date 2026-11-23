package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

final class PatternPreviewNativeSlots {
    private final ECOMachineInterfaceBlockEntity<?> host;
    private final Player player;
    private final long epoch;
    private final int[] approved;
    private final boolean[] visible = new boolean[CraftingInterfaceUI.PREVIEW_BUFFER_SLOTS];

    PatternPreviewNativeSlots(ECOMachineInterfaceBlockEntity<?> host, Player player, CompoundTag data) {
        this.host = host;
        this.player = player;
        epoch = data.getLong("epoch");
        approved = data.getIntArray("slots");
    }

    Slot slot(int visual) {
        int index = approved.length == visible.length ? approved[visual] : -1;
        return host.createNativePatternSlot(player, epoch, index, () -> visible[visual]);
    }

    boolean isVisible(int visual) {
        return visible[visual];
    }

    void updateView(long viewEpoch, int[] wanted) {
        for (int visual = 0; visual < visible.length; visual++) {
            visible[visual] = epoch == viewEpoch && approved.length == visible.length
                && approved[visual] >= 0 && approved[visual] == wanted[visual];
        }
    }

    boolean needsWindow(long viewEpoch, int[] wanted) {
        for (int visual = 0; visual < wanted.length; visual++) {
            if (wanted[visual] >= 0 && (viewEpoch != epoch
                    || approved.length != wanted.length || approved[visual] != wanted[visual])) return true;
        }
        return false;
    }
}
