package cn.dancingsnow.neoecoae.integration.recipe;

/** Pixel geometry shared by both viewers; additional card rows expand the footer, never cover inputs. */
public record WorkstationRecipeInfoLayout(int cardCount) {
    public static final int INFO_X = 4, INFO_Y = 72, INFO_WIDTH = 160;
    public static final int CARD_SIZE = 16, CARD_PADDING = 2, CARD_GAP = 2;
    public static final int TEXT_X = 7, ROW_HEIGHT = 8;
    public static final int MAX_CARD_COLUMNS = 4;

    public WorkstationRecipeInfoLayout {
        if (cardCount < 0) throw new IllegalArgumentException("Negative card count");
    }

    public int cardColumns() { return Math.min(cardCount, MAX_CARD_COLUMNS); }
    public int cardRows() { return (cardCount + MAX_CARD_COLUMNS - 1) / MAX_CARD_COLUMNS; }
    public int infoHeight() { return Math.max(20, CARD_PADDING * 2 + cardRows() * (CARD_SIZE + CARD_GAP) - CARD_GAP); }
    public int extraHeight() { return infoHeight() - 20; }
    public int backgroundHeight() { return 96 + extraHeight(); }
    public int extraSlotsY() { return 100 + extraHeight(); }
    public int textWidth() {
        int right = cardCount == 0 ? INFO_X + INFO_WIDTH - 3
            : INFO_X + INFO_WIDTH - CARD_PADDING - cardColumns() * (CARD_SIZE + CARD_GAP) + CARD_GAP - 2;
        return right - TEXT_X;
    }
    public int energyY() { return INFO_Y + (infoHeight() - ROW_HEIGHT * 2) / 2 + 1; }
    public int extraY() { return energyY() + ROW_HEIGHT; }

    public int cardX(int index) {
        checkIndex(index);
        int rowCount = Math.min(cardColumns(), cardCount - index / MAX_CARD_COLUMNS * MAX_CARD_COLUMNS);
        return INFO_X + INFO_WIDTH - CARD_PADDING - CARD_SIZE
            - (rowCount - 1 - index % MAX_CARD_COLUMNS) * (CARD_SIZE + CARD_GAP);
    }
    public int cardY(int index) {
        checkIndex(index);
        return INFO_Y + CARD_PADDING + index / MAX_CARD_COLUMNS * (CARD_SIZE + CARD_GAP);
    }
    public int height(int fluidInputs, int outputs) {
        int rows = (fluidInputs + 8) / 9 + (outputs + 8) / 9;
        return rows == 0 ? backgroundHeight() : extraSlotsY() + rows * 18;
    }
    public boolean containsText(double x, double y) {
        return x >= TEXT_X - 1 && x < TEXT_X + textWidth() + 1
            && y >= energyY() - 1 && y < extraY() - 1 + ROW_HEIGHT;
    }
    private void checkIndex(int index) {
        if (index < 0 || index >= cardCount) throw new IndexOutOfBoundsException(index);
    }
}
