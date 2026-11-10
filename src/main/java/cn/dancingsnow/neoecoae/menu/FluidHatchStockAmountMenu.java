package cn.dancingsnow.neoecoae.menu;

/** The long fields/actions added only for ECO fluid hatch hosts of AE2's stock submenu. */
public interface FluidHatchStockAmountMenu {
    long neoecoae$getInitialStockAmount();
    long neoecoae$getMaxStockAmount();
    void neoecoae$confirmStockAmount(long amount);
}
