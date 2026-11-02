package cn.dancingsnow.neoecoae.gui.workstation;

/** Pixel offsets for the workstation's pattern viewport, independent of frame rate and row boundaries. */
public final class WorkstationPatternScroll {
    public static final int COLUMNS = 9;
    public static final int VISIBLE_ROWS = 7;
    public static final int ROW_HEIGHT = 18;
    public static final int VIEWPORT_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;

    private double offset;
    private double target;
    private double maximum;

    public void setSlotCount(int slots) {
        int rows = (Math.max(0, slots) + COLUMNS - 1) / COLUMNS;
        maximum = Math.max(0, rows * ROW_HEIGHT - VIEWPORT_HEIGHT);
        offset = clamp(offset);
        target = clamp(target);
    }

    public void scrollBy(double pixels) {
        scrollTo(target + pixels);
    }

    public void scrollTo(double pixels) {
        target = clamp(pixels);
    }

    public void jumpTo(double pixels) {
        offset = target = clamp(pixels);
    }

    public void stop() {
        target = offset;
    }

    public void advance(double seconds) {
        if (seconds <= 0 || !Double.isFinite(seconds)) return;
        offset += (target - offset) * -Math.expm1(-20 * seconds);
        if (Math.abs(target - offset) < 0.01) offset = target;
    }

    public double offset() {
        return offset;
    }

    public double maximum() {
        return maximum;
    }

    /** Cell top relative to the viewport; partial rows intentionally have negative positions. */
    public double cellY(int slot) {
        return slot / COLUMNS * ROW_HEIGHT - offset;
    }

    public boolean isVisible(int slot) {
        double y = cellY(slot);
        return y < VIEWPORT_HEIGHT && y + ROW_HEIGHT > 0;
    }

    private double clamp(double pixels) {
        return Double.isFinite(pixels) ? Math.clamp(pixels, 0, maximum) : 0;
    }
}
