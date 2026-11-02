package cn.dancingsnow.neoecoae.client.gui.workstation;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.api.upgrades.Upgrades;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.client.Point;
import cn.dancingsnow.neoecoae.gui.workstation.WorkstationPatternScroll;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.HashSet;
import java.util.Set;

/** Native AE2 pattern-provider interactions with a smoothly scrolling, clipped pattern viewport. */
public class LargeIntegratedWorkingStationPatternProviderScreen
    extends PatternProviderScreen<LargeIntegratedWorkingStationPatternProviderMenu> {

    private final Set<Integer> shiftDraggedSlots = new HashSet<>();
    private final WorkstationPatternScroll patternScroll = new WorkstationPatternScroll();
    private final Scrollbar patternScrollbar;
    private boolean shiftDragging;
    private boolean scrollbarDragging;
    private double scrollbarGrabOffset;
    private long lastScrollFrame;

    private static final int PATTERN_LEFT = 7;
    private static final int PATTERN_TOP = 22;
    private static final int PATTERN_WIDTH = 162;
    // The middle inventory row has matching horizontal and vertical separators between all nine cells.
    private static final Blitter PATTERN_ROW_BACKGROUND = Blitter
        .texture("guis/large_integrated_working_station_interface.png", 256, 256)
        .src(7, 178, PATTERN_WIDTH, WorkstationPatternScroll.ROW_HEIGHT);

    public LargeIntegratedWorkingStationPatternProviderScreen(
        LargeIntegratedWorkingStationPatternProviderMenu menu,
        Inventory playerInventory,
        Component title,
        ScreenStyle style
    ) {
        super(menu, playerInventory, title, style);
        patternScroll.setSlotCount(menu.getPatternSlotCount());
        patternScrollbar = new PatternScrollbar();
        patternScrollbar.setCaptureMouseWheel(false);
        patternScrollbar.setRange(0, (int) patternScroll.maximum(), WorkstationPatternScroll.ROW_HEIGHT);
        widgets.add("patternScrollbar", patternScrollbar);
        try {
            widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE),
                () -> Upgrades.getTooltipLinesForMachine(menu.getCapacityUpgrades().getUpgradableItem())));
        } catch (IllegalStateException existingPanel) {
            if (!"Duplicate id: upgrades".equals(existingPanel.getMessage())) throw existingPanel;
            // AE2 provider addons may already supply this native panel with the same workstation upgrade slots.
        }
    }

    @Override
    protected void init() {
        super.init();
        lastScrollFrame = System.nanoTime();
        updatePatternSlots();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        long now = System.nanoTime();
        if (lastScrollFrame != 0) patternScroll.advance((now - lastScrollFrame) / 1_000_000_000.0);
        lastScrollFrame = now;
        patternScrollbar.setCurrentScroll((int) Math.round(patternScroll.offset()));
        updatePatternSlots();
        // EAEP adds these private widgets to the superclass, including after a resize.
        hideSmartDoublingWidget("eap$SmartDoublingToggle");
        hideSmartDoublingWidget("eap$PerProviderLimitInput");
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        shiftDraggedSlots.clear();
        shiftDragging = false;
        var scrollbarBounds = patternScrollbar.getBounds();
        double thumbTop = topPos + scrollbarBounds.getY() + scrollbarThumbOffset();
        if (button == 0 && patternScroll.maximum() > 0
            && mouseX >= leftPos + scrollbarBounds.getX()
            && mouseX < leftPos + scrollbarBounds.getX() + scrollbarBounds.getWidth()
            && mouseY >= thumbTop && mouseY < thumbTop + Scrollbar.SMALL.handleHeight()) {
            scrollbarDragging = true;
            scrollbarGrabOffset = mouseY - thumbTop;
            patternScroll.stop();
            return true;
        }
        // Keep the physical slot still while a click or item-drag gesture is being resolved.
        if (overPatterns(mouseX, mouseY)) patternScroll.stop();
        // The press itself stays a vanilla Shift-click; only the slots crossed afterwards are ours.
        Slot slot = button == 0 && event.hasShiftDown() && menu.getCarried().isEmpty() ? slotAt(mouseX, mouseY) : null;
        shiftDragging = slot != null;
        if (slot != null) shiftDraggedSlots.add(slot.index);
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && overPatterns(mouseX, mouseY) && patternScroll.maximum() > 0) {
            patternScroll.scrollBy(-deltaY * WorkstationPatternScroll.ROW_HEIGHT);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void updatePatternSlots() {
        int visibleSlotCount = menu.getPatternSlotCount();
        patternScroll.setSlotCount(visibleSlotCount);
        patternScrollbar.setRange(0, (int) patternScroll.maximum(), WorkstationPatternScroll.ROW_HEIGHT);
        var slots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        int pixels = (int) Math.floor(patternScroll.offset());
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            boolean visible = index < visibleSlotCount && patternScroll.isVisible(index);
            if (slot instanceof AppEngSlot aeSlot) aeSlot.setActive(visible);
            setSlotPos(slot, visible ? PATTERN_LEFT + 1 + index % WorkstationPatternScroll.COLUMNS * 18 : -9999,
                visible ? PATTERN_TOP + 1 + index / WorkstationPatternScroll.COLUMNS * 18 - pixels : -9999);
        }
    }

    @Override
    public void drawBG(GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        clipPatterns(graphics);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(offsetX, (float) (offsetY - fractionalOffset()));
            // Keep the complete seven-row AE2-style grid visible even when capacity cards have not
            // unlocked every row yet. Locked rows remain visual placeholders because their slots stay
            // inactive in updatePatternSlots(), but the fixed viewport must not collapse into a blank area.
            int pixels = (int) Math.floor(patternScroll.offset());
            int physicalRows = (menu.getSlots(SlotSemantics.ENCODED_PATTERN).size()
                + WorkstationPatternScroll.COLUMNS - 1) / WorkstationPatternScroll.COLUMNS;
            int rowsToDraw = Math.max(WorkstationPatternScroll.VISIBLE_ROWS, physicalRows);
            for (int row = 0; row < rowsToDraw; row++) {
                PATTERN_ROW_BACKGROUND.dest(PATTERN_LEFT, PATTERN_TOP + row * WorkstationPatternScroll.ROW_HEIGHT
                    - pixels).blit(graphics);
            }
        } finally {
            graphics.pose().popMatrix();
            graphics.disableScissor();
        }
    }

    @Override
    public void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (menu.getSlotSemantic(slot) != SlotSemantics.ENCODED_PATTERN) {
            super.extractSlot(graphics, slot, mouseX, mouseY);
            return;
        }
        boolean clipped = needsPatternClipping(slot);
        if (clipped) clipPatterns(graphics);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(0, (float) -fractionalOffset());
            super.extractSlot(graphics, slot, mouseX, mouseY);
        } finally {
            graphics.pose().popMatrix();
            if (clipped) graphics.disableScissor();
        }
    }

    // TODO(26.1 port): vanilla 26.1 draws the hovered-slot highlight in private methods without a per-slot hook,
    // so the sub-pixel scroll offset/clipping is no longer applied to the highlight sprite of pattern slots.

    // Vanilla's isHovering(Slot, double, double) is private at compile time, but AE2's access transformer
    // widens it to protected at runtime, so this declaration overrides it in game.
    protected boolean isHovering(Slot slot, double mouseX, double mouseY) {
        if (menu.getSlotSemantic(slot) == SlotSemantics.ENCODED_PATTERN) {
            return slot.isActive() && overPatterns(mouseX, mouseY)
                && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY + fractionalOffset());
        }
        return isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY);
    }

    private static final java.lang.reflect.Field SLOT_X = slotField("x");
    private static final java.lang.reflect.Field SLOT_Y = slotField("y");

    private static java.lang.reflect.Field slotField(String name) {
        try {
            var field = Slot.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Slot.x/y are final at compile time; AE2's access transformer makes them mutable at runtime. */
    private static void setSlotPos(Slot slot, int x, int y) {
        if (slot.x == x && slot.y == y) return;
        try {
            SLOT_X.setInt(slot, x);
            SLOT_Y.setInt(slot, y);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot move workstation pattern slot", e);
        }
    }

    private boolean overPatterns(double mouseX, double mouseY) {
        double x = mouseX - leftPos - PATTERN_LEFT;
        double y = mouseY - topPos - PATTERN_TOP;
        return x >= 0 && x < PATTERN_WIDTH && y >= 0 && y < WorkstationPatternScroll.VIEWPORT_HEIGHT;
    }

    private double fractionalOffset() {
        return patternScroll.offset() - Math.floor(patternScroll.offset());
    }

    private boolean needsPatternClipping(Slot slot) {
        double top = slot.y - fractionalOffset() - 1;
        return top < PATTERN_TOP || top + 18 > PATTERN_TOP + WorkstationPatternScroll.VIEWPORT_HEIGHT;
    }

    private double scrollbarThumbOffset() {
        return patternScroll.maximum() == 0 ? 0 : patternScroll.offset() / patternScroll.maximum()
            * (patternScrollbar.getBounds().getHeight() - Scrollbar.SMALL.handleHeight());
    }

    private void clipPatterns(GuiGraphicsExtractor graphics) {
        // Keep the fixed outer frame visible while rows and their separators move through the viewport.
        graphics.enableScissor(leftPos + PATTERN_LEFT + 1, topPos + PATTERN_TOP + 1,
            leftPos + PATTERN_LEFT + PATTERN_WIDTH - 1,
            topPos + PATTERN_TOP + WorkstationPatternScroll.VIEWPORT_HEIGHT - 1);
    }

    private final class PatternScrollbar extends Scrollbar {
        private PatternScrollbar() {
            super(Scrollbar.SMALL);
        }

        @Override
        public Rect2i getBounds() {
            var bounds = super.getBounds();
            // The supplied track has one more inner pixel to the left than AE2's small thumb.
            return new Rect2i(bounds.getX(), bounds.getY(), bounds.getWidth() + 1, bounds.getHeight());
        }

        @Override
        public void drawForegroundLayer(GuiGraphicsExtractor graphics, Rect2i bounds, Point mouse) {
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(0, (float) scrollbarThumbOffset());
                var sprite = patternScroll.maximum() == 0
                    ? Scrollbar.SMALL.disabledSprite() : Scrollbar.SMALL.enabledSprite();
                var track = getBounds();
                Blitter.guiSprite(sprite).dest(track.getX(), track.getY(), track.getWidth(),
                    Scrollbar.SMALL.handleHeight()).blit(graphics);
            } finally {
                graphics.pose().popMatrix();
            }
        }

        @Override
        public boolean onMouseDown(Point mouse, int button) {
            boolean handled = super.onMouseDown(mouse, button);
            if (handled) patternScroll.scrollTo(getCurrentScroll());
            return handled;
        }

        @Override
        public boolean onMouseWheel(Point mouse, double delta) {
            if (patternScroll.maximum() == 0) return false;
            patternScroll.scrollBy(-delta * WorkstationPatternScroll.ROW_HEIGHT);
            return true;
        }

        @Override
        public void tick() {
            int previous = getCurrentScroll();
            super.tick();
            if (getCurrentScroll() != previous) patternScroll.scrollTo(getCurrentScroll());
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0 && scrollbarDragging) {
            var bounds = patternScrollbar.getBounds();
            double travel = bounds.getHeight() - Scrollbar.SMALL.handleHeight();
            patternScroll.jumpTo((mouseY - topPos - bounds.getY() - scrollbarGrabOffset)
                / travel * patternScroll.maximum());
            patternScrollbar.setCurrentScroll((int) Math.round(patternScroll.offset()));
            updatePatternSlots();
            return true;
        }
        if (button == 0 && shiftDragging && event.hasShiftDown()) {
            // One drag event per frame: a fast swipe jumps over whole slots, so walk the segment.
            int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dragX), Math.abs(dragY)) / 4));
            for (int i = steps - 1; i >= 0; i--) {
                quickMove(slotAt(mouseX - dragX * i / steps, mouseY - dragY * i / steps));
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            shiftDragging = false;
            scrollbarDragging = false;
        }
        return super.mouseReleased(event);
    }

    /** Vanilla's private findSlot: screen coordinates, active slots only. */
    private Slot slotAt(double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            if (isHovering(slot, mouseX, mouseY) && slot.isActive()) return slot;
        }
        return null;
    }

    private void quickMove(Slot slot) {
        if (slot == null || !shiftDraggedSlots.add(slot.index)) return;
        slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
    }

    private void hideSmartDoublingWidget(String name) {
        try {
            var field = PatternProviderScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            if (field.get(this) instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.visible = false;
                widget.active = false;
                widget.setFocused(false);
                removeWidget(widget);
            }
        } catch (NoSuchFieldException absent) {
            // EAEP is optional.
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot hide workstation smart doubling", failure);
        }
    }
}
