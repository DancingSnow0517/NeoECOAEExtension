package cn.dancingsnow.neoecoae.multiblock.network;

/** A system controller that may carry a network switch and a logical-network frequency. */
public interface NENetworkSwitchHost {
    boolean hasNormalNetworkSwitch();

    boolean hasHighEnergyNetworkSwitch();

    default boolean hasNetworkSwitch() {
        return hasNormalNetworkSwitch() || hasHighEnergyNetworkSwitch();
    }

    boolean hasNetworkFrequency();

    int getNetworkFrequency();

    void assignNetworkFrequency(int frequency);
}
