package cn.dancingsnow.neoecoae.gui.workstation;

/** Continuous task positions, with movement bounded by the rows received from the server. */
final class LargeWorkstationTaskScroll {
    static final int ROW_HEIGHT = 26;
    static final int VIEWPORT_HEIGHT = 76;
    private double offset;
    private double target;
    private int total;
    private int first;
    private int count;
    private boolean pendingJump;

    boolean receive(int total, int first, int count) {
        this.total = Math.max(0, total);
        offset = clamp(offset);
        target = clamp(target);
        double position = pendingJump ? target : offset;
        // An older reply must not replace the rows beneath a view that has already reversed direction.
        if (first * ROW_HEIGHT > position || (first + count) * ROW_HEIGHT <
            Math.min(this.total * ROW_HEIGHT, position + LargeWorkstationUI.PAGE_SIZE * ROW_HEIGHT)) return false;
        this.first = first;
        this.count = count;
        if (pendingJump) {
            offset = target;
            pendingJump = false;
        }
        return true;
    }

    void scrollBy(double rows) {
        if (!Double.isFinite(rows)) return;
        target = clamp(target + rows * ROW_HEIGHT);
        pendingJump = false;
    }

    void jumpTo(double pixels) {
        target = clamp(pixels);
        pendingJump = target < first * ROW_HEIGHT || target > loadedMaximum();
        if (!pendingJump) offset = target;
    }

    void stop() {
        target = offset;
        pendingJump = false;
    }

    void advance(double seconds) {
        if (pendingJump || seconds <= 0 || !Double.isFinite(seconds)) return;
        double next = offset + (target - offset) * -Math.expm1(-20 * seconds);
        if (Math.abs(next - target) < 0.01) next = target;
        // Wait at the buffer edge until the next window arrives, keeping both partial rows populated.
        offset = Math.clamp(next, Math.min(offset, first * ROW_HEIGHT), Math.max(offset, loadedMaximum()));
    }

    int requestedRow() {
        if (pendingJump) return (int) (target / ROW_HEIGHT);
        double row = offset / ROW_HEIGHT;
        int requested = target >= offset
            ? Math.min((int) (target / ROW_HEIGHT), (int) Math.floor(row) + LargeWorkstationUI.SNAPSHOT_PADDING)
            : Math.max((int) (target / ROW_HEIGHT), (int) Math.ceil(row) - LargeWorkstationUI.SNAPSHOT_PADDING);
        return Math.clamp(requested, 0, Math.max(0, total - LargeWorkstationUI.PAGE_SIZE));
    }

    double offset() { return offset; }
    double thumbOffset() { return pendingJump ? target : offset; }
    double maximum() { return Math.max(0, total - LargeWorkstationUI.PAGE_SIZE) * ROW_HEIGHT; }
    double rowShift() { return first * ROW_HEIGHT - offset; }
    int firstVisibleRow() { return (int) (offset / ROW_HEIGHT); }
    int lastVisibleRow() {
        return Math.min(total, (int) Math.ceil((offset + VIEWPORT_HEIGHT) / ROW_HEIGHT));
    }

    private double loadedMaximum() {
        return Math.min(maximum(), Math.max(0, first + count - LargeWorkstationUI.PAGE_SIZE) * ROW_HEIGHT);
    }

    private double clamp(double pixels) {
        return Double.isFinite(pixels) ? Math.clamp(pixels, 0, maximum()) : 0;
    }
}
