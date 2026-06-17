package cn.dancingsnow.neoecoae.gui.ldlib.support;

public record NEIntegratedWorkingStationUiState(
        long energy,
        long maxEnergy,
        int progress,
        int maxProgress,
        int requiredEnergy,
        boolean working,
        boolean autoExport) {
    public static NEIntegratedWorkingStationUiState empty() {
        return new NEIntegratedWorkingStationUiState(0, 0, 0, 0, 0, false, false);
    }
}
