package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import net.minecraft.network.chat.Component;

/**
 * Per-machine configuration for a native ECO Screen.
 * <p>
 * Each constant provides the log identifier and whether a Test button
 * should be shown.
 * </p>
 */
public record NEMachineScreenConfig(String testLogName, boolean showTestButton) {

    // ── Pre-defined configs ──

    /** Storage Controller — real status display, no Test button. */
    public static final NEMachineScreenConfig STORAGE_CONTROLLER =
        new NEMachineScreenConfig("Storage", false);

    public static final NEMachineScreenConfig COMPUTATION_CONTROLLER =
        new NEMachineScreenConfig("Computation", false);

    public static final NEMachineScreenConfig CRAFTING_CONTROLLER =
        new NEMachineScreenConfig("Crafting", false);

    public static final NEMachineScreenConfig INTEGRATED_WORKING_STATION =
        new NEMachineScreenConfig("IWS", true);

    public static final NEMachineScreenConfig CRAFTING_PATTERN_BUS =
        new NEMachineScreenConfig("Pattern Bus", true);

    public static final NEMachineScreenConfig FLUID_HATCH =
        new NEMachineScreenConfig("Fluid Hatch", true);

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
