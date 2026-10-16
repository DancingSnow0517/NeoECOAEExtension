package cn.dancingsnow.neoecoae.gui.crafting;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Everything the F-series controller artwork needs to draw itself.
 *
 * <p>Slot data is stored per <em>cell</em> (screen position, left to right), so mirroring is resolved on the
 * server and the client never needs to know about it. Parallel-core tiers are 0 (none) or 1..3 (L4/L6/L9).
 */
public final class CraftingControllerPanelState {
    private final cn.dancingsnow.neoecoae.core.ui.CraftingControllerPanelState model;

    public static final int SLOT_COUNT = 11;
    public static final int FULL = 1000;
    public static final CraftingControllerPanelState UNFORMED =
        new CraftingControllerPanelState(false, 0, 0, 0, 0, false, false, 0, 0);

    private static final int TIER_MASK = 3;
    private static final String FORMED = "formed";
    private static final String HOST_TIER = "host";
    private static final String FX_CELLS = "cells";
    private static final String UPPER = "up";
    private static final String LOWER = "low";
    private static final String COOLANT = "coolant";
    private static final String SWITCH = "switch";
    private static final String QUEUE = "queue";
    private static final String ENERGY = "energy";

    public CraftingControllerPanelState(boolean formed, int hostTier, int fxCells, int upperCores, int lowerCores, boolean coolantOverlay, boolean switchOverlay, int queuePermille, int energyPermille) {
        this.model = new cn.dancingsnow.neoecoae.core.ui.CraftingControllerPanelState(formed, hostTier, fxCells, upperCores, lowerCores, coolantOverlay, switchOverlay, queuePermille, energyPermille);
    }

    public boolean hasFx(int cell) {
        return model.hasFx(cell);
    }

    public int fxCount() {
        return model.fxCount();
    }

    public int upperTier(int cell) {
        return model.upperTier(cell);
    }

    public int lowerTier(int cell) {
        return model.lowerTier(cell);
    }

    /** Index {@code 0} is the FX worker next to the controller. Slots past {@link #SLOT_COUNT} are not drawn. */
    public static CraftingControllerPanelState of(
        int hostTier,
        List<Integer> upperNearestFirst,
        List<Integer> lowerNearestFirst,
        int fxCount,
        boolean mirrored,
        boolean coolantOverlay,
        boolean switchOverlay,
        int queuePermille,
        int energyPermille
    ) {
        return fromCore(cn.dancingsnow.neoecoae.core.ui.CraftingControllerPanelState.of(hostTier,
            upperNearestFirst, lowerNearestFirst, fxCount, mirrored, coolantOverlay, switchOverlay,
            queuePermille, energyPermille));
    }

    public static boolean coolantVisible(boolean activeCooling, FluidStack fluid, int amount) {
        return cn.dancingsnow.neoecoae.core.ui.CraftingControllerPanelState.coolantVisible(activeCooling,
            fluid != null && !fluid.isEmpty(), amount);
    }

    public static int cellOf(int slot, boolean mirrored) {
        return cn.dancingsnow.neoecoae.core.ui.CraftingControllerPanelState.cellOf(slot, mirrored);
    }

    public static int permille(long numerator, long denominator) {
        return cn.dancingsnow.neoecoae.core.ui.CraftingControllerPanelState.permille(numerator, denominator);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(FORMED, formed());
        tag.putInt(HOST_TIER, hostTier());
        tag.putInt(FX_CELLS, fxCells());
        tag.putInt(UPPER, upperCores());
        tag.putInt(LOWER, lowerCores());
        tag.putBoolean(COOLANT, coolantOverlay());
        tag.putBoolean(SWITCH, switchOverlay());
        tag.putInt(QUEUE, queuePermille());
        tag.putInt(ENERGY, energyPermille());
        return tag;
    }

    public static CraftingControllerPanelState fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return UNFORMED;
        }
        return new CraftingControllerPanelState(
            tag.getBoolean(FORMED),
            tag.getInt(HOST_TIER),
            tag.getInt(FX_CELLS),
            tag.getInt(UPPER),
            tag.getInt(LOWER),
            tag.getBoolean(COOLANT),
            tag.getBoolean(SWITCH),
            tag.getInt(QUEUE),
            tag.getInt(ENERGY)
        );
    }

    private static CraftingControllerPanelState fromCore(cn.dancingsnow.neoecoae.core.ui.CraftingControllerPanelState value) {
        return new CraftingControllerPanelState(value.formed(), value.hostTier(), value.fxCells(), value.upperCores(), value.lowerCores(), value.coolantOverlay(), value.switchOverlay(), value.queuePermille(), value.energyPermille());
    }

    public boolean formed() { return model.formed(); }
    public int hostTier() { return model.hostTier(); }
    public int fxCells() { return model.fxCells(); }
    public int upperCores() { return model.upperCores(); }
    public int lowerCores() { return model.lowerCores(); }
    public boolean coolantOverlay() { return model.coolantOverlay(); }
    public boolean switchOverlay() { return model.switchOverlay(); }
    public int queuePermille() { return model.queuePermille(); }
    public int energyPermille() { return model.energyPermille(); }

    @Override public boolean equals(Object other) {
        return other instanceof CraftingControllerPanelState state && model.equals(state.model);
    }
    @Override public int hashCode() { return model.hashCode(); }
    @Override public String toString() { return model.toString(); }
}
