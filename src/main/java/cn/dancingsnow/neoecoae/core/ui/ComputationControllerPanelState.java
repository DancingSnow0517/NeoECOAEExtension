package cn.dancingsnow.neoecoae.core.ui;


import java.util.List;

/**
 * Everything the computation controller artwork needs to draw itself.
 *
 * <p>Slot data is stored per <em>cell</em> (screen position, left to right), so mirroring is resolved on the
 * server and the client never needs to know about it.
 */
public record ComputationControllerPanelState(
    boolean formed,
    int hostTier,
    int cells,
    int upperCores,
    int coreTiers,
    int lowerCores
) {
    public static final int SLOT_COUNT = 11;
    public static final ComputationControllerPanelState UNFORMED =
        new ComputationControllerPanelState(false, 0, 0, 0, 0, 0);

    private static final int TIER_MASK = 3;

    public ComputationControllerPanelState {
        hostTier = Math.clamp(hostTier, 0, 3);
    }

    public boolean hasCell(int cell) {
        return cell >= 0 && cell < SLOT_COUNT && (cells >> cell & 1) != 0;
    }

    public int upperTier(int cell) {
        return tierAt(upperCores, cell);
    }

    public int coreTier(int cell) {
        return tierAt(coreTiers, cell);
    }

    public int lowerTier(int cell) {
        return tierAt(lowerCores, cell);
    }

    /** Index {@code 0} is the threading core next to the controller. Slots past {@link #SLOT_COUNT} are not drawn. */
    public static ComputationControllerPanelState of(
        int hostTier,
        List<Integer> upperNearestFirst,
        List<Integer> coreNearestFirst,
        List<Integer> lowerNearestFirst,
        boolean mirrored
    ) {
        int upper = 0;
        int core = 0;
        int lower = 0;
        int cells = 0;
        int visible = Math.min(coreNearestFirst.size(), SLOT_COUNT);
        for (int slot = 0; slot < visible; slot++) {
            int cell = cellOf(slot, mirrored);
            cells |= 1 << cell;
            upper |= tierBits(upperNearestFirst, slot) << cell * 2;
            core |= tierBits(coreNearestFirst, slot) << cell * 2;
            lower |= tierBits(lowerNearestFirst, slot) << cell * 2;
        }
        return new ComputationControllerPanelState(true, hostTier, cells, upper, core, lower);
    }

    public static int cellOf(int slot, boolean mirrored) {
        return mirrored ? SLOT_COUNT - 1 - slot : slot;
    }

    private static int tierAt(int packed, int cell) {
        return cell < 0 || cell >= SLOT_COUNT ? 0 : packed >> cell * 2 & TIER_MASK;
    }

    private static int tierBits(List<Integer> tiers, int slot) {
        return slot < tiers.size() ? Math.clamp(tiers.get(slot), 0, 3) : 0;
    }
}
