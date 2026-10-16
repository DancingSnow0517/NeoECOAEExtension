package cn.dancingsnow.neoecoae.gui.computation;

import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * Everything the computation controller artwork needs to draw itself.
 *
 * <p>Slot data is stored per <em>cell</em> (screen position, left to right), so mirroring is resolved on the
 * server and the client never needs to know about it.
 */
public final class ComputationControllerPanelState {
    private final cn.dancingsnow.neoecoae.core.ui.ComputationControllerPanelState model;

    public static final int SLOT_COUNT = 11;
    public static final ComputationControllerPanelState UNFORMED =
        new ComputationControllerPanelState(false, 0, 0, 0, 0, 0);

    private static final int TIER_MASK = 3;
    private static final String FORMED = "formed";
    private static final String HOST_TIER = "host";
    private static final String CELLS = "cells";
    private static final String UPPER = "up";
    private static final String CORE = "core";
    private static final String LOWER = "low";

    public ComputationControllerPanelState(boolean formed, int hostTier, int cells, int upperCores, int coreTiers, int lowerCores) {
        this.model = new cn.dancingsnow.neoecoae.core.ui.ComputationControllerPanelState(formed, hostTier, cells, upperCores, coreTiers, lowerCores);
    }

    public boolean hasCell(int cell) {
        return model.hasCell(cell);
    }

    public int upperTier(int cell) {
        return model.upperTier(cell);
    }

    public int coreTier(int cell) {
        return model.coreTier(cell);
    }

    public int lowerTier(int cell) {
        return model.lowerTier(cell);
    }

    /** Index {@code 0} is the threading core next to the controller. Slots past {@link #SLOT_COUNT} are not drawn. */
    public static ComputationControllerPanelState of(
        int hostTier,
        List<Integer> upperNearestFirst,
        List<Integer> coreNearestFirst,
        List<Integer> lowerNearestFirst,
        boolean mirrored
    ) {
        return fromCore(cn.dancingsnow.neoecoae.core.ui.ComputationControllerPanelState.of(hostTier,
            upperNearestFirst, coreNearestFirst, lowerNearestFirst, mirrored));
    }

    public static int cellOf(int slot, boolean mirrored) {
        return cn.dancingsnow.neoecoae.core.ui.ComputationControllerPanelState.cellOf(slot, mirrored);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(FORMED, formed());
        tag.putInt(HOST_TIER, hostTier());
        tag.putInt(CELLS, cells());
        tag.putInt(UPPER, upperCores());
        tag.putInt(CORE, coreTiers());
        tag.putInt(LOWER, lowerCores());
        return tag;
    }

    public static ComputationControllerPanelState fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return UNFORMED;
        }
        return new ComputationControllerPanelState(
            tag.getBoolean(FORMED),
            tag.getInt(HOST_TIER),
            tag.getInt(CELLS),
            tag.getInt(UPPER),
            tag.getInt(CORE),
            tag.getInt(LOWER)
        );
    }

    private static ComputationControllerPanelState fromCore(cn.dancingsnow.neoecoae.core.ui.ComputationControllerPanelState value) {
        return new ComputationControllerPanelState(value.formed(), value.hostTier(), value.cells(), value.upperCores(), value.coreTiers(), value.lowerCores());
    }

    public boolean formed() { return model.formed(); }
    public int hostTier() { return model.hostTier(); }
    public int cells() { return model.cells(); }
    public int upperCores() { return model.upperCores(); }
    public int coreTiers() { return model.coreTiers(); }
    public int lowerCores() { return model.lowerCores(); }

    @Override public boolean equals(Object other) {
        return other instanceof ComputationControllerPanelState state && model.equals(state.model);
    }
    @Override public int hashCode() { return model.hashCode(); }
    @Override public String toString() { return model.toString(); }
}
