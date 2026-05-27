package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import net.minecraft.network.chat.Component;

/**
 * Per-machine configuration for a native ECO Screen.
 * <p>
 * Each constant provides the log identifier used by the test button.
 * Concrete Screen classes pass their config to {@link NEBaseMachineScreen}.
 * </p>
 */
public record NEMachineScreenConfig(String testLogName) {

    // ── Pre-defined configs ──

    public static final NEMachineScreenConfig STORAGE_CONTROLLER =
        new NEMachineScreenConfig("Storage");

    public static final NEMachineScreenConfig COMPUTATION_CONTROLLER =
        new NEMachineScreenConfig("Computation");

    public static final NEMachineScreenConfig CRAFTING_CONTROLLER =
        new NEMachineScreenConfig("Crafting");

    public static final NEMachineScreenConfig INTEGRATED_WORKING_STATION =
        new NEMachineScreenConfig("IWS");

    public static final NEMachineScreenConfig CRAFTING_PATTERN_BUS =
        new NEMachineScreenConfig("Pattern Bus");

    public static final NEMachineScreenConfig FLUID_HATCH =
        new NEMachineScreenConfig("Fluid Hatch");

    // ── Log helpers ──

    /**
     * Builds the log message for the test button click.
     * @return e.g. {@code [NeoECOAE] Native Storage UI test button clicked}
     */
    public String buildLogMessage() {
        return "[NeoECOAE] Native " + testLogName + " UI test button clicked";
    }

    /**
     * Builds a log message that appends the machine title.
     * Used by Fluid Hatch screens that serve two block types.
     */
    public String buildLogMessage(Component title) {
        return buildLogMessage() + ": " + title.getString();
    }
}
