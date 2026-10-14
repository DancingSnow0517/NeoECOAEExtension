package cn.dancingsnow.neoecoae.gui.theme;

/**
 * Named colours for GUI text and overlays. Each value is copied verbatim from the call sites it names, so changing
 * any of them changes rendered pixels. The inks are stored as RGB without alpha; call sites that OR in 0xFF000000
 * keep doing so.
 */
public final class Palette {
    // Dark ink on light panels (storage, workstation, computation, multiblock, HostPanelElements, EMI/JEI).
    // These three inks intentionally retain the colours of their different panels.
    public static final int INK = 0x3F3D52;
    public static final int INK_403E53 = 0x403E53;
    public static final int INK_413F54 = 0x413F54;

    // Host panel text on dark backgrounds (HostText).
    public static final int HOST_PRIMARY = 0xD6D0E0;
    public static final int HOST_VALUE = 0x8377FF;
    public static final int HOST_USED = 0x00FC00;
    public static final int HOST_MUTED = 0xAAA4B2;
    public static final int HOST_WARNING = 0xFFD65A;
    public static final int HOST_ORANGE = 0xFF9A3D;
    public static final int HOST_ERROR = 0xFF6A75;

    // Crafting controller HUD text (CraftingControllerArtwork).
    public static final int CRAFTING_PRIMARY = 0xFFE4E4EE;
    public static final int CRAFTING_SECONDARY = 0xFFB4B6CC;
    public static final int CRAFTING_QUEUE_VALUE = 0xB4E2FF;
    public static final int CRAFTING_PARALLEL_VALUE = 0xFFC2ED;
    public static final int CRAFTING_OVERFLOW_VALUE = 0xFFDB99;

    // Crafting report header (ECOCraftReportHeaderRenderer).
    public static final int REPORT_TEXT = 0xFFFFFF;
    public static final int REPORT_BYTES_TRACK = 0xFF6A6A72;
    public static final int REPORT_BYTES_FILL = 0xFF3D9B62;

    // Crafting graph (GraphRenderer, CompactTreeRenderer, ECOCraftingGraphScreen).
    public static final int GRAPH_TEXT = 0xFFE8EDF2;
    public static final int GRAPH_MUTED = 0xFFAAB3BD;

    // Translucent cell overlays in the crafting confirm and exact-material tables.
    public static final int OVERLAY_MISSING = 0x1AFF0000;
    public static final int OVERLAY_CYCLE = 0x1AB86BFF;
    public static final int OVERLAY_FUZZY_PLANNING = 0x264CAF70;

    private Palette() {
    }
}
