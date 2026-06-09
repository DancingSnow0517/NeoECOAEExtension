package cn.dancingsnow.neoecoae.gui.ldlib.support;

import net.minecraftforge.fluids.FluidStack;

public record NEIntegratedWorkingStationUiState(
        long energy,
        long maxEnergy,
        int progress,
        int maxProgress,
        int requiredEnergy,
        boolean working,
        boolean autoExport,
        FluidStack inputFluid,
        FluidStack outputFluid) {
    public static NEIntegratedWorkingStationUiState empty() {
        return new NEIntegratedWorkingStationUiState(0, 0, 0, 0, 0, false, false, FluidStack.EMPTY, FluidStack.EMPTY);
    }
}
