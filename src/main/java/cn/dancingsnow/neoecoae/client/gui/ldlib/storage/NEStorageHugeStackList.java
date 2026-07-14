package cn.dancingsnow.neoecoae.client.gui.ldlib.storage;

import static cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageLayout.*;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageHugeStackState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageTextFormatter;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.math.BigInteger;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/** Stateful renderer and input model for infinite-domain huge stacks. */
public final class NEStorageHugeStackList {
    private static final float TEXT_SCALE = 0.72F;
    private static final double SCROLL_SPEED = 20.0D;
    private static final double SCROLL_RESPONSE_MS = 75.0D;
    private static final BigInteger TWO_LINE_THRESHOLD = BigInteger.valueOf(1024L)
            .pow(6)
            .multiply(BigInteger.valueOf(92L))
            .add(BigInteger.valueOf(9L))
            .divide(BigInteger.TEN);

    private double scrollPixels;
    private double targetScrollPixels;
    private long lastFrameNanos;
    private int lastPage;

    public boolean scrollBy(NEStorageUiState state, double wheelDelta) {
        ensurePage(state);
        double maxScroll = maxScrollPixels(state);
        double previous = targetScrollPixels;
        targetScrollPixels = Mth.clamp(targetScrollPixels - wheelDelta * SCROLL_SPEED, 0.0D, maxScroll);
        return targetScrollPixels != previous || maxScroll > 0.0D;
    }

    public void draw(
            GuiGraphics g, Font font, IntUnaryOperator screenX, IntUnaryOperator screenY, NEStorageUiState state) {
        if (!isVisible(state)) {
            scrollPixels = 0.0D;
            targetScrollPixels = 0.0D;
            return;
        }
        ensurePage(state);
        List<NEStorageHugeStackState> hugeStacks = state.hugeStacks();
        updateSmoothScroll(maxScrollPixels(state));
        int x = screenX.applyAsInt(HUGE_STACK_PANEL_X);
        int y = screenY.applyAsInt(HUGE_STACK_PANEL_Y - (int) Math.round(scrollPixels));
        int clipX = screenX.applyAsInt(HUGE_STACK_PANEL_X);
        int clipY = screenY.applyAsInt(HUGE_STACK_PANEL_Y);
        int contentHeight = contentHeight(state);
        g.enableScissor(clipX, clipY, clipX + HUGE_STACK_PANEL_W, clipY + contentHeight);
        for (int i = 0; i < hugeStacks.size(); i++) {
            drawRow(g, font, hugeStacks.get(i), x, y + i * HUGE_STACK_ROW_H);
        }
        g.disableScissor();
        drawScrollbar(g, screenX, screenY, state);
        drawPageFooter(g, font, screenX, screenY, state);
    }

    public boolean drawTooltip(
            GuiGraphics graphics,
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEStorageUiState state,
            int mouseX,
            int mouseY) {
        if (!isVisible(state) || !containsEntries(screenX, screenY, state, mouseX, mouseY)) {
            return false;
        }
        NEStorageHugeStackState entry = entryAt(screenY, state, mouseY);
        if (entry == null) {
            return false;
        }
        graphics.renderComponentTooltip(
                font,
                List.of(
                        entry.key().getDisplayName().copy().withStyle(ChatFormatting.AQUA),
                        Component.literal(NELDLibText.preciseHugeAmount(entry.amount()))
                                .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_USED))),
                mouseX,
                mouseY);
        return true;
    }

    public boolean contains(IntUnaryOperator screenX, IntUnaryOperator screenY, double mouseX, double mouseY) {
        return Widget.isMouseOver(
                screenX.applyAsInt(HUGE_STACK_PANEL_X),
                screenY.applyAsInt(HUGE_STACK_PANEL_Y),
                HUGE_STACK_PANEL_W,
                HUGE_STACK_PANEL_H,
                mouseX,
                mouseY);
    }

    public OptionalInt pageRequestAt(
            IntUnaryOperator screenX, IntUnaryOperator screenY, NEStorageUiState state, double mouseX, double mouseY) {
        ensurePage(state);
        if (state.hugeStackPageCount() <= 1) {
            return OptionalInt.empty();
        }
        int footerY = screenY.applyAsInt(HUGE_STACK_PANEL_Y + HUGE_STACK_PANEL_H - HUGE_STACK_PAGE_FOOTER_H);
        int panelX = screenX.applyAsInt(HUGE_STACK_PANEL_X);
        if (!Widget.isMouseOver(panelX, footerY, HUGE_STACK_PANEL_W, HUGE_STACK_PAGE_FOOTER_H, mouseX, mouseY)) {
            return OptionalInt.empty();
        }
        if (mouseX < panelX + 14 && state.hugeStackPage() > 0) {
            return OptionalInt.of(state.hugeStackPage() - 1);
        }
        if (mouseX >= panelX + HUGE_STACK_PANEL_W - 14 && state.hugeStackPage() + 1 < state.hugeStackPageCount()) {
            return OptionalInt.of(state.hugeStackPage() + 1);
        }
        return OptionalInt.empty();
    }

    public static boolean isVisible(NEStorageUiState state) {
        return state.infiniteMode() && !state.hugeStacks().isEmpty();
    }

    public double targetScrollPixels() {
        return targetScrollPixels;
    }

    public void restore(double rememberedScrollPixels) {
        scrollPixels = rememberedScrollPixels;
        targetScrollPixels = rememberedScrollPixels;
    }

    private static void drawRow(GuiGraphics g, Font font, NEStorageHugeStackState entry, int x, int y) {
        if (entry.key() instanceof AEFluidKey fluidKey) {
            FluidStack stack = fluidKey.toStack(1);
            NELDLibAe2StyleRenderer.drawFluidIcon(g, x, y + 1, 16, stack);
        } else {
            ItemStack displayStack = entry.key().wrapForDisplayOrFilter();
            if (displayStack.isEmpty()) {
                displayStack = GenericStack.wrapInItemStack(entry.key(), 1L);
            }
            g.renderItem(displayStack, x, y + 1);
        }
        int textX = x + 19;
        int textW = Math.max(1, Math.round((HUGE_STACK_PANEL_W - 23) / TEXT_SCALE));
        boolean showName = NEStorageTextFormatter.parseAmount(entry.amount()).compareTo(TWO_LINE_THRESHOLD) >= 0;
        g.pose().pushPose();
        g.pose().translate(textX, y + (showName ? 1 : 4), 0.0F);
        g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        if (showName) {
            String name = font.plainSubstrByWidth(entry.key().getDisplayName().getString(), textW);
            g.drawString(font, name, 0, 0, 0xFF61AFEF, false);
            g.drawString(font, NELDLibText.hugeAmount(entry.amount()), 0, 10, NELDLibStyle.DARK_TEXT_USED, false);
        } else {
            g.drawString(font, NELDLibText.hugeAmount(entry.amount()), 0, 0, NELDLibStyle.DARK_TEXT_USED, false);
        }
        g.pose().popPose();
    }

    private void drawScrollbar(
            GuiGraphics g, IntUnaryOperator screenX, IntUnaryOperator screenY, NEStorageUiState state) {
        List<NEStorageHugeStackState> hugeStacks = state.hugeStacks();
        double maxScroll = maxScrollPixels(state);
        if (maxScroll <= 0.0D) {
            scrollPixels = 0.0D;
            return;
        }
        int trackX = screenX.applyAsInt(HUGE_STACK_PANEL_X + HUGE_STACK_PANEL_W - 4);
        int trackY = screenY.applyAsInt(HUGE_STACK_PANEL_Y);
        int viewportH = contentHeight(state);
        int contentH = hugeStacks.size() * HUGE_STACK_ROW_H;
        int thumbH = Math.max(10, viewportH * viewportH / Math.max(viewportH, contentH));
        int thumbY = trackY + (int) Math.round((viewportH - thumbH) * scrollPixels / maxScroll);
        g.fill(trackX, trackY, trackX + 2, trackY + viewportH, 0xAA17141E);
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF8377FF);
    }

    private static void drawPageFooter(
            GuiGraphics g, Font font, IntUnaryOperator screenX, IntUnaryOperator screenY, NEStorageUiState state) {
        if (state.hugeStackPageCount() <= 1) {
            return;
        }
        int x = screenX.applyAsInt(HUGE_STACK_PANEL_X);
        int y = screenY.applyAsInt(HUGE_STACK_PANEL_Y + HUGE_STACK_PANEL_H - HUGE_STACK_PAGE_FOOTER_H);
        int enabled = NELDLibStyle.DARK_TEXT_VALUE;
        int disabled = NELDLibStyle.DARK_TEXT_MUTED;
        g.drawString(font, "<", x + 4, y + 1, state.hugeStackPage() > 0 ? enabled : disabled, false);
        String pageText = (state.hugeStackPage() + 1) + "/" + state.hugeStackPageCount();
        g.drawString(font, pageText, x + (HUGE_STACK_PANEL_W - font.width(pageText)) / 2, y + 1, disabled, false);
        g.drawString(
                font,
                ">",
                x + HUGE_STACK_PANEL_W - 10,
                y + 1,
                state.hugeStackPage() + 1 < state.hugeStackPageCount() ? enabled : disabled,
                false);
    }

    @Nullable private NEStorageHugeStackState entryAt(IntUnaryOperator screenY, NEStorageUiState state, int mouseY) {
        int localY = mouseY - screenY.applyAsInt(HUGE_STACK_PANEL_Y) + (int) Math.round(scrollPixels);
        int index = localY / HUGE_STACK_ROW_H;
        List<NEStorageHugeStackState> hugeStacks = state.hugeStacks();
        return index >= 0 && index < hugeStacks.size() ? hugeStacks.get(index) : null;
    }

    private static double maxScrollPixels(NEStorageUiState state) {
        return Math.max(0, state.hugeStacks().size() * HUGE_STACK_ROW_H - contentHeight(state));
    }

    private boolean containsEntries(
            IntUnaryOperator screenX, IntUnaryOperator screenY, NEStorageUiState state, double mouseX, double mouseY) {
        return Widget.isMouseOver(
                screenX.applyAsInt(HUGE_STACK_PANEL_X),
                screenY.applyAsInt(HUGE_STACK_PANEL_Y),
                HUGE_STACK_PANEL_W,
                contentHeight(state),
                mouseX,
                mouseY);
    }

    private static int contentHeight(NEStorageUiState state) {
        return HUGE_STACK_PANEL_H - (state.hugeStackPageCount() > 1 ? HUGE_STACK_PAGE_FOOTER_H : 0);
    }

    private void ensurePage(NEStorageUiState state) {
        if (lastPage == state.hugeStackPage()) {
            return;
        }
        scrollPixels = 0.0D;
        targetScrollPixels = 0.0D;
        lastPage = state.hugeStackPage();
    }

    private void updateSmoothScroll(double maxScroll) {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
        }
        double elapsedMs = Math.min(100.0D, (now - lastFrameNanos) / 1_000_000.0D);
        lastFrameNanos = now;
        targetScrollPixels = Mth.clamp(targetScrollPixels, 0.0D, maxScroll);
        double response = 1.0D - Math.exp(-elapsedMs / SCROLL_RESPONSE_MS);
        scrollPixels += (targetScrollPixels - scrollPixels) * response;
        scrollPixels = Mth.clamp(scrollPixels, 0.0D, maxScroll);
        if (Math.abs(targetScrollPixels - scrollPixels) < 0.05D) {
            scrollPixels = targetScrollPixels;
        }
    }
}
