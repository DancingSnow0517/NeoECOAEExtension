package cn.dancingsnow.neoecoae.client.gui.graph;

/**
 * Planning-graph palette. Three neutral layers carry the structure; status colors only mark strips, amounts and
 * outlines. The light palette darkens every status color so it keeps its contrast on white.
 */
record GraphTheme(boolean light, int bar, int canvas, int panel, int hover, int line, int edge, int edgeRejected,
        int text, int muted, int faint, int accent, int pending, int missing, int stock, int byproduct,
        int checkpoint, int step) {
    static final GraphTheme DARK = new GraphTheme(false, 0xff0e1116, 0xff151a21, 0xff1c222b, 0xff262d38,
        0xff2d3542, 0xff3b4557, 0xff4a3a42, 0xffd8dde5, 0xff8b94a3, 0xff5d6675, 0xff4cc3d6, 0xffe3a840,
        0xffe2645e, 0xff5cc489, 0xffa08cf0, 0xff7bb2d4, 0xff83c5be);
    static final GraphTheme LIGHT = new GraphTheme(true, 0xffe6eaf0, 0xfff3f5f8, 0xffffffff, 0xffe9edf3,
        0xffcdd4de, 0xffaab3c1, 0xffd9c3c6, 0xff1e2530, 0xff5a6472, 0xff8d96a4, 0xff0f8a9e, 0xffc07c10,
        0xffcc3f38, 0xff2a9358, 0xff6f57d4, 0xff3d7aa6, 0xff2f8f86);

    private static GraphTheme current = DARK;

    static GraphTheme current() { return current; }

    static void toggle() {
        current = current.light ? DARK : LIGHT;
        // Raster tiles hold the previous palette.
        GraphRenderer.resourcesReloaded();
    }

    /** Same color with the given alpha. */
    static int alpha(int color, int alpha) { return alpha << 24 | color & 0xffffff; }
}
