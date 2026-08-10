package cn.dancingsnow.neoecoae.gui.ldlib.state;

import net.minecraft.core.BlockPos;

public record NECraftingInterfaceUiState(
        BlockPos pos,
        boolean formed,
        boolean targetOnline,
        String search,
        boolean showSubstitutions,
        boolean showFluidSubstitutions,
        int entryCount,
        int scrollRow,
        int maxScrollRow,
        String transferStatusKey,
        int transferStatusArg1,
        int transferStatusArg2,
        int noSpace,
        int incompatible) {
    public static NECraftingInterfaceUiState empty(BlockPos pos) {
        return new NECraftingInterfaceUiState(
                pos,
                false,
                false,
                "",
                true,
                true,
                0,
                0,
                0,
                "gui.neoecoae.host.crafting.pattern_transfer.ready",
                0,
                0,
                0,
                0);
    }
}
