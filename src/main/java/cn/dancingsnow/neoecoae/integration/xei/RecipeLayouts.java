package cn.dancingsnow.neoecoae.integration.xei;

/** Shared recipe geometry. Slot coordinates include the frame; JEI places contents one pixel inside it. */
public final class RecipeLayouts {
    private RecipeLayouts() {}

    public static final class Cooling {
        public static final int WIDTH = 100;
        public static final int HEIGHT = 60;
        public static final int INPUT_X = 9;
        public static final int OUTPUT_X = 71;
        public static final int SLOT_Y = 10;
        public static final int SLOT_SIZE = 18;
        public static final int PROGRESS_X = 35;
        public static final int PROGRESS_Y = 5;
        public static final int PROGRESS_SIZE = 30;
        public static final int TEXT_X = 5;
        public static final int TEXT_Y = 40;
        public static final int TEXT_LINE_HEIGHT = 10;

        private Cooling() {}
    }

    public static final class Workstation {
        public static final int WIDTH = 168;
        public static final int HEIGHT = 75;
        public static final int LARGE_HEIGHT = 139;
        public static final int FLUID_INPUT_X = 4;
        public static final int FLUID_OUTPUT_X = 146;
        public static final int FLUID_Y = 8;
        public static final int FLUID_WIDTH = 18;
        public static final int FLUID_HEIGHT = 60;
        public static final int FLUID_CAPACITY = 16_000;
        public static final int ITEM_INPUT_X = 37;
        public static final int ITEM_INPUT_Y = 11;
        public static final int ITEM_COLUMNS = 3;
        public static final int SLOT_SIZE = 18;
        public static final int ITEM_OUTPUT_X = 113;
        public static final int ITEM_OUTPUT_Y = 30;
        public static final int PROGRESS_X = 136;
        public static final int PROGRESS_Y = 30;
        public static final int PROGRESS_WIDTH = 6;
        public static final int PROGRESS_HEIGHT = 18;
        public static final int ENERGY_X = 24;
        public static final int ENERGY_Y = 66;

        private Workstation() {}
    }
}
