package cn.dancingsnow.neoecoae.core.network;

/** Network Switch multipliers shared by live clusters and the crafting capability calculator. */
public final class NetworkSwitchMultipliers {
    /** Multiplier contributed by a normal Network Switch. */
    public static final int NORMAL = 2;
    /** Multiplier contributed by a High-Energy Network Switch. */
    public static final int HIGH_ENERGY = 8;

    private NetworkSwitchMultipliers() {
    }

    /** The effective multiplier of a host: high-energy wins over normal, and no switch gives 1. */
    public static int of(boolean normalSwitch, boolean highEnergySwitch) {
        if (highEnergySwitch) {
            return HIGH_ENERGY;
        }
        return normalSwitch ? NORMAL : 1;
    }
}
