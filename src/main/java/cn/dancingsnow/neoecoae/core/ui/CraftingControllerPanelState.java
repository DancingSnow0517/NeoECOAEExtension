package cn.dancingsnow.neoecoae.core.ui;


import java.util.List;

/**
 * Everything the F-series controller artwork needs to draw itself.
 *
 * <p>Slot data is stored per <em>cell</em> (screen position, left to right), so mirroring is resolved on the
 * server and the client never needs to know about it. Parallel-core tiers are 0 (none) or 1..3 (L4/L6/L9).
 */
public record CraftingControllerPanelState(
    boolean formed,
    int hostTier,
    int fxCells,
    int upperCores,
    int lowerCores,
    boolean coolantOverlay,
    boolean switchOverlay,
    int queuePermille,
    int energyPermille
) {
    public static final int SLOT_COUNT = 11;
    public static final int FULL = 1000;
    public static final CraftingControllerPanelState UNFORMED =
        new CraftingControllerPanelState(false, 0, 0, 0, 0, false, false, 0, 0);

    private static final int TIER_MASK = 3;

    public CraftingControllerPanelState {
        hostTier = Math.clamp(hostTier, 0, 3);
        queuePermille = Math.clamp(queuePermille, 0, FULL);
        energyPermille = Math.clamp(energyPermille, 0, FULL);
    }

    public boolean hasFx(int cell) {
        return cell >= 0 && cell < SLOT_COUNT && (fxCells >> cell & 1) != 0;
    }

    public int fxCount() {
        return Integer.bitCount(fxCells);
    }

    public int upperTier(int cell) {
        return tierAt(upperCores, cell);
    }

    public int lowerTier(int cell) {
        return tierAt(lowerCores, cell);
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
        int upper = 0;
        int lower = 0;
        int cells = 0;
        int visible = Math.min(fxCount, SLOT_COUNT);
        for (int slot = 0; slot < visible; slot++) {
            int cell = cellOf(slot, mirrored);
            cells |= 1 << cell;
            upper |= tierBits(upperNearestFirst, slot) << cell * 2;
            lower |= tierBits(lowerNearestFirst, slot) << cell * 2;
        }
        return new CraftingControllerPanelState(
            true, hostTier, cells, upper, lower, coolantOverlay, switchOverlay, queuePermille, energyPermille);
    }

    public static boolean coolantVisible(boolean activeCooling, boolean coolantPresent, int amount) {
        return activeCooling && coolantPresent && amount > 0;
    }

    public static int cellOf(int slot, boolean mirrored) {
        return mirrored ? SLOT_COUNT - 1 - slot : slot;
    }

    public static int permille(long numerator, long denominator) {
        if (denominator <= 0L || numerator <= 0L) {
            return 0;
        }
        if (numerator >= denominator) {
            return FULL;
        }
        return (int) Math.round(numerator * (double) FULL / denominator);
    }

    private static int tierAt(int packed, int cell) {
        return cell < 0 || cell >= SLOT_COUNT ? 0 : packed >> cell * 2 & TIER_MASK;
    }

    private static int tierBits(List<Integer> tiers, int slot) {
        return slot < tiers.size() ? Math.clamp(tiers.get(slot), 0, 3) : 0;
    }
}
