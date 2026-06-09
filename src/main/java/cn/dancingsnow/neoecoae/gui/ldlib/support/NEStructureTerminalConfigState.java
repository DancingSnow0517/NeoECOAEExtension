package cn.dancingsnow.neoecoae.gui.ldlib.support;

import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMaterialRequirements;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public record NEStructureTerminalConfigState(
        int length,
        int minLength,
        int maxLength,
        int tier,
        StructureTerminalHostType hostType,
        StructureTerminalMode operationMode,
        List<NEStructureTerminalUiState.BuildMaterialEntry> materials) {
    public static NEStructureTerminalConfigState empty() {
        return new NEStructureTerminalConfigState(
                StructureTerminalItem.DEFAULT_BUILD_LENGTH,
                StructureTerminalItem.MIN_BUILD_LENGTH,
                StructureTerminalItem.MIN_BUILD_LENGTH,
                StructureTerminalHostType.DEFAULT_TIER,
                StructureTerminalHostType.DEFAULT,
                StructureTerminalMode.BUILD,
                List.of());
    }

    public static NEStructureTerminalConfigState fromStack(Player player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof StructureTerminalItem)) {
            return empty();
        }
        int min = StructureTerminalItem.MIN_BUILD_LENGTH;
        int max = StructureTerminalItem.getMaxBuildLength(stack);
        int length = StructureTerminalItem.getBuildLength(stack, max);
        int tier = StructureTerminalItem.getHostTier(stack);
        StructureTerminalHostType hostType = StructureTerminalItem.getHostType(stack);
        StructureTerminalMode mode = StructureTerminalItem.getOperationMode(stack);
        return new NEStructureTerminalConfigState(
                length,
                min,
                max,
                tier,
                hostType,
                mode,
                StructureTerminalMaterialRequirements.collect(player, hostType, tier, length));
    }
}
