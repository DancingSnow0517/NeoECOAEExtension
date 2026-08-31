package cn.dancingsnow.neoecoae.client.craftinggraph;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Allocates two stable attachment ports on each side of every graph node. */
final class GraphPortAllocator {
    enum Side { TOP, RIGHT, BOTTOM, LEFT }

    private final Map<Integer, int[]> useCounts = new HashMap<>();

    void reserveSide(int nodeId, Side side) {
        int[] counts = useCounts.computeIfAbsent(nodeId, ignored -> new int[8]);
        counts[index(side, 0)]++;
        counts[index(side, 1)]++;
    }

    GraphLayoutSnapshot.Point attach(GraphLayoutSnapshot.Box box, float towardX, float towardY,
            boolean outgoing, @Nullable Side forcedSide) {
        Side preferred = forcedSide == null ? nearestSide(box, towardX, towardY) : forcedSide;
        Side side = forcedSide == null ? availableSide(box.nodeId(), preferred, outgoing) : preferred;
        int slot = availableSlot(box.nodeId(), side, outgoing);
        useCounts.computeIfAbsent(box.nodeId(), ignored -> new int[8])[index(side, slot)]++;
        return fixedPoint(box, side, slot);
    }

    private Side availableSide(int nodeId, Side preferred, boolean outgoing) {
        for (Side side : sideOrder(preferred)) {
            int[] counts = useCounts.computeIfAbsent(nodeId, ignored -> new int[8]);
            int preferredSlot = preferredSlot(side, outgoing);
            if (counts[index(side, preferredSlot)] == 0 || counts[index(side, 1 - preferredSlot)] == 0) {
                return side;
            }
        }
        return preferred;
    }

    private int availableSlot(int nodeId, Side side, boolean outgoing) {
        int[] counts = useCounts.computeIfAbsent(nodeId, ignored -> new int[8]);
        int preferred = preferredSlot(side, outgoing);
        int alternate = 1 - preferred;
        if (counts[index(side, preferred)] == 0) return preferred;
        if (counts[index(side, alternate)] == 0) return alternate;
        return counts[index(side, preferred)] <= counts[index(side, alternate)] ? preferred : alternate;
    }

    private static int preferredSlot(Side side, boolean outgoing) {
        boolean clockwiseSlot = side == Side.TOP || side == Side.RIGHT;
        return outgoing == clockwiseSlot ? 1 : 0;
    }

    private static int index(Side side, int slot) {
        return side.ordinal() * 2 + slot;
    }

    static Side nearestSide(GraphLayoutSnapshot.Box box, float x, float y) {
        float dx = x - box.centerX();
        float dy = y - box.centerY();
        if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? Side.RIGHT : Side.LEFT;
        return dy >= 0 ? Side.BOTTOM : Side.TOP;
    }

    private static Side[] sideOrder(Side preferred) {
        return switch (preferred) {
            case TOP -> new Side[] { Side.TOP, Side.LEFT, Side.RIGHT, Side.BOTTOM };
            case RIGHT -> new Side[] { Side.RIGHT, Side.TOP, Side.BOTTOM, Side.LEFT };
            case BOTTOM -> new Side[] { Side.BOTTOM, Side.RIGHT, Side.LEFT, Side.TOP };
            case LEFT -> new Side[] { Side.LEFT, Side.BOTTOM, Side.TOP, Side.RIGHT };
        };
    }

    static GraphLayoutSnapshot.Point evenlySpacedPoint(GraphLayoutSnapshot.Box box, Side side,
            int position, int count) {
        float fraction = (position + 1f) / (count + 1f);
        return switch (side) {
            case TOP, BOTTOM -> new GraphLayoutSnapshot.Point(box.x() + box.width() * fraction,
                side == Side.TOP ? box.y() : box.y() + box.height());
            case LEFT, RIGHT -> new GraphLayoutSnapshot.Point(
                side == Side.LEFT ? box.x() : box.x() + box.width(), box.y() + box.height() * fraction);
        };
    }

    private static GraphLayoutSnapshot.Point fixedPoint(GraphLayoutSnapshot.Box box, Side side, int slot) {
        float fraction = (slot + 1) / 3f;
        return switch (side) {
            case TOP -> new GraphLayoutSnapshot.Point(box.x() + box.width() * fraction, box.y());
            case RIGHT -> new GraphLayoutSnapshot.Point(box.x() + box.width(), box.y() + box.height() * fraction);
            case BOTTOM -> new GraphLayoutSnapshot.Point(box.x() + box.width() * (1 - fraction),
                box.y() + box.height());
            case LEFT -> new GraphLayoutSnapshot.Point(box.x(), box.y() + box.height() * (1 - fraction));
        };
    }
}
